/*
 * Copyright 2018 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.eclair.wallet;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.concurrent.atomic.AtomicReference;

import akka.actor.ActorSystem;
import akka.dispatch.OnComplete;
import akka.dispatch.OnFailure;
import akka.pattern.Patterns;
import akka.util.Timeout;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.Transaction;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.Globals;
import fr.acinq.eclair.Kit;
import fr.acinq.eclair.blockchain.electrum.ElectrumEclairWallet;
import fr.acinq.eclair.blockchain.electrum.ElectrumWallet;
import fr.acinq.eclair.channel.CMD_GETINFO$;
import fr.acinq.eclair.channel.Channel;
import fr.acinq.eclair.channel.Register;
import fr.acinq.eclair.io.NodeURI;
import fr.acinq.eclair.io.Peer;
import fr.acinq.eclair.payment.PaymentLifecycle;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.wallet.events.BitcoinPaymentFailedEvent;
import fr.acinq.eclair.wallet.events.ChannelRawDataEvent;
import fr.acinq.eclair.wallet.events.LNNewChannelFailureEvent;
import fr.acinq.eclair.wallet.events.NetworkChannelsCountEvent;
import fr.acinq.eclair.wallet.events.NotificationEvent;
import fr.acinq.eclair.wallet.events.WalletStateUpdateEvent;
import fr.acinq.eclair.wallet.events.XpubEvent;
import fr.acinq.eclair.wallet.utils.Constants;
import scala.Symbol;
import scala.Tuple2;
import scala.collection.Iterable;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

public class App extends Application {

  public final static String TAG = "App";
  private final static ExchangeRate exchangeRate = new ExchangeRate();
  public final ActorSystem system = ActorSystem.apply("system");
  public AtomicReference<Satoshi> onChainBalance = new AtomicReference<>(new Satoshi(0));
  public AtomicReference<String> pin = new AtomicReference<>(null);
  public AppKit appKit;
  private DBHelper dbHelper;
  private String walletAddress = "N/A";

  /**
   * Update the application's exchange rate in BTCUSD and BTCEUR.
   *
   * @param eurRate value of 1 BTC in EURO
   * @param usdRate value of 1 BTC in USD
   */
  public static void updateExchangeRate(final float eurRate, final float usdRate) {
    exchangeRate.eurRate = eurRate;
    exchangeRate.usdRate = usdRate;
  }

  /**
   * Returns the value of 1 BTC in EURO.
   *
   * @return
   */
  public static float getEurRate() {
    return exchangeRate.eurRate;
  }

  /**
   * Returns the value of 1 BTC in USD.
   *
   * @return
   */
  public static float getUsdRate() {
    return exchangeRate.usdRate;
  }

  @Override
  public void onCreate() {
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this);
    }
    super.onCreate();
  }

  /**
   * Returns true if the wallet is not compatible with the local datas.
   *
   * @return
   */
  public boolean hasBreakingChanges() {
    return !appKit.isDBCompatible;
  }

  /**
   * Return application's version
   *
   * @return
   */
  public String getVersion() {
    try {
      return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
    } catch (PackageManager.NameNotFoundException e) {
    }
    return "N/A";
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleNotification(NotificationEvent notificationEvent) {
    NotificationCompat.Builder notification = new NotificationCompat.Builder(this.getBaseContext())
      .setVisibility(NotificationCompat.VISIBILITY_SECRET)
      .setPriority(Notification.PRIORITY_HIGH)
      .setDefaults(Notification.DEFAULT_SOUND)
      .setVibrate(new long[]{0})
      .setSmallIcon(R.drawable.eclair_256x256)
      .setContentTitle(notificationEvent.title)
      .setContentText(notificationEvent.message)
      .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationEvent.bigMessage));
    NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    mNotificationManager.notify(notificationEvent.tag, notificationEvent.id, notification.build());
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleNewWalletAddress(final ElectrumWallet.NewWalletReceiveAddress addressEvent) {
    walletAddress = addressEvent.address();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleWalletBalanceEvent(WalletStateUpdateEvent event) {
    this.onChainBalance.set(event.balance);
  }

  public String getWalletAddress() {
    return this.walletAddress;
  }

  /**
   * Asks the eclair node to asynchronously execute a Lightning payment. Future failure is silent.
   *
   * @param paymentRequest Lightning payment request
   * @param amountMsat     Amount of the payment in millisatoshis. Overrides the amount provided by the payment request!
   */
  public void sendLNPayment(final PaymentRequest paymentRequest, final long amountMsat, final boolean capMaxFee) {
    Long finalCltvExpiry = Channel.MIN_CLTV_EXPIRY();
    if (paymentRequest.minFinalCltvExpiry().isDefined() && paymentRequest.minFinalCltvExpiry().get() instanceof Long) {
      finalCltvExpiry = (Long) paymentRequest.minFinalCltvExpiry().get();
    }
    Double maxFeePct = capMaxFee ? 0.03 : Double.MAX_VALUE;
    Patterns.ask(appKit.eclairKit.paymentInitiator(),
      new PaymentLifecycle.SendPayment(amountMsat, paymentRequest.paymentHash(), paymentRequest.nodeId(), paymentRequest.routingInfo(), finalCltvExpiry + 1, 10, maxFeePct),
      new Timeout(Duration.create(1, "seconds"))).onFailure(new OnFailure() {
      @Override
      public void onFailure(Throwable failure) throws Throwable {
      }
    }, system.dispatcher());
  }

  /**
   * Executes an onchain transaction with electrum.
   *
   * @param amountSat amount to send in satoshis
   * @param address   recipient of the tx
   * @param feesPerKw fees for the tx
   */
  public void sendBitcoinPayment(final Satoshi amountSat, final String address, final long feesPerKw) {
    try {
      Future<String> fBitcoinPayment = appKit.electrumWallet.sendPayment(amountSat, address, feesPerKw);
      fBitcoinPayment.onComplete(new OnComplete<String>() {
        @Override
        public void onComplete(final Throwable t, final String txId) {
          if (t == null) {
            Log.i(TAG, "Successfully sent tx " + txId);
          } else {
            Log.w(TAG, "could not send bitcoin tx " + txId + "  with cause=" + t.getMessage());
            EventBus.getDefault().post(new BitcoinPaymentFailedEvent(t.getLocalizedMessage()));
          }
        }
      }, this.system.dispatcher());
    } catch (Throwable t) {
      Log.w(TAG, "could not send bitcoin tx with cause=" + t.getMessage());
      EventBus.getDefault().post(new BitcoinPaymentFailedEvent(t.getLocalizedMessage()));
    }
  }

  /**
   * Empties the onchain wallet by sending all the available onchain balance to the given address,
   * using the given fees which will be substracted from the available balance.
   *
   * @param address   recipient of the tx
   * @param feesPerKw fees for the tx
   */
  public void sendAllOnchain(final String address, final long feesPerKw) {
    try {
      final Future<Tuple2<Transaction, Satoshi>> fCreateSendAll = appKit.electrumWallet.sendAll(address, feesPerKw);
      fCreateSendAll.onComplete(new OnComplete<Tuple2<Transaction, Satoshi>>() {
        @Override
        public void onComplete(final Throwable t, final Tuple2<Transaction, Satoshi> res) {
          if (t == null) {
            if (res != null) {
              Log.i(TAG, "onComplete: commiting spend all tx");
              final Future fSendAll = appKit.electrumWallet.commit(res._1());
              fSendAll.onComplete(new OnComplete<Boolean>() {
                @Override
                public void onComplete(Throwable failure, Boolean success) throws Throwable {
                  if (!success) {
                    Log.w(TAG, "could not send empty wallet tx");
                    EventBus.getDefault().post(new BitcoinPaymentFailedEvent("broadcast failed"));
                  }
                }
              }, system.dispatcher());
            } else {
              Log.w(TAG, "could not create send all tx");
              EventBus.getDefault().post(new BitcoinPaymentFailedEvent("tx creation failed"));
            }
          } else {
            Log.w(TAG, "could not send all balance with cause=" + t.getMessage());
            EventBus.getDefault().post(new BitcoinPaymentFailedEvent(t.getLocalizedMessage()));
          }
        }
      }, this.system.dispatcher());
    } catch (Throwable t) {
      Log.w(TAG, "could not send send all balance with cause=" + t.getMessage());
      EventBus.getDefault().post(new BitcoinPaymentFailedEvent(t.getLocalizedMessage()));
    }
  }

  /**
   * Asks the eclair node to asynchronously open a channel with a node. Completes with a
   * {@link akka.pattern.AskTimeoutException} after the timeout has expired.
   *
   * @param timeout    Connection future timeout
   * @param onComplete Callback executed once the future completes (with success or failure)
   * @param nodeURI    Uri of the node to connect to
   * @param open       channel to create, contains the capacity of the channel, in satoshis
   */
  public void openChannel(final FiniteDuration timeout, final OnComplete<Object> onComplete,
                          final NodeURI nodeURI, final Peer.OpenChannel open) {
    if (nodeURI.nodeId() != null && nodeURI.address() != null && open != null) {
      final OnComplete<Object> onConnectComplete = new OnComplete<Object>() {
        @Override
        public void onComplete(Throwable throwable, Object result) throws Throwable {
          if (throwable != null) {
            EventBus.getDefault().post(new LNNewChannelFailureEvent(throwable.getMessage()));
          } else if ("connected".equals(result.toString()) || "already connected".equals(result.toString())) {
            final Future<Object> openFuture = Patterns.ask(appKit.eclairKit.switchboard(), open, new Timeout(timeout));
            openFuture.onComplete(onComplete, system.dispatcher());
          } else {
            EventBus.getDefault().post(new LNNewChannelFailureEvent(result.toString()));
          }
        }
      };
      final Future<Object> connectFuture = Patterns.ask(appKit.eclairKit.switchboard(), new Peer.Connect(nodeURI), new Timeout(timeout));
      connectFuture.onComplete(onConnectComplete, system.dispatcher());
    }
  }

  /**
   * Broadcast a transaction using the payload.
   *
   * @param payload
   */
  public void broadcastTx(final String payload) {
    final Transaction tx = (Transaction) Transaction.read(payload);
    Future<Object> future = appKit.electrumWallet.commit(tx);
    try {
      Boolean success = (Boolean) Await.result(future, Duration.create(500, "milliseconds"));
      if (success) Log.i(TAG, "successful broadcast of " + tx.txid());
      else Log.w(TAG, "cannot broadcast " + tx.txid());
    } catch (Exception e) {
      Log.w(TAG, "failed broadcast of " + tx.txid(), e);
    }
  }

  /**
   * Returns the eclair node's public key.
   *
   * @return
   */
  public String nodePublicKey() {
    return appKit.eclairKit.nodeParams().privateKey().publicKey().toBin().toString();
  }

  public long estimateSlowFees() {
    return Math.max(Globals.feeratesPerKB().get().blocks_72() / 1000, 1);
  }

  public long estimateMediumFees() {
    return Math.max(Globals.feeratesPerKB().get().blocks_12() / 1000, estimateSlowFees());
  }

  public long estimateFastFees() {
    return Math.max(Globals.feeratesPerKB().get().blocks_2() / 1000, estimateMediumFees());
  }

  /**
   * Asynchronously asks for the Lightning Network's channels count. Dispatch a {@link NetworkChannelsCountEvent} containing the channel count.
   * The call timeouts fails after 10 seconds. When the call fails, the network's channels count will be -1.
   */
  public void getNetworkChannelsCount() {
    Future<Object> future = Patterns.ask(appKit.eclairKit.router(), Symbol.apply("channels"), new Timeout(Duration.create(10, "seconds")));
    future.onComplete(new OnComplete<Object>() {
      @Override
      public void onComplete(Throwable throwable, Object o) throws Throwable {
        if (throwable == null && o != null && o instanceof Iterable) {
          EventBus.getDefault().post(new NetworkChannelsCountEvent(((Iterable) o).size()));
        } else {
          EventBus.getDefault().post(new NetworkChannelsCountEvent(-1));
        }
      }
    }, system.dispatcher());
  }

  /**
   * Asynchronously ask for the raw json data of a local channel.
   */
  public void getLocalChannelRawData(final BinaryData channelId) {
    Register.Forward<CMD_GETINFO$> forward = new Register.Forward<>(channelId, CMD_GETINFO$.MODULE$);
    Future<Object> future = Patterns.ask(appKit.eclairKit.register(), forward, new Timeout(Duration.create(5, "seconds")));
    future.onComplete(new OnComplete<Object>() {
      @Override
      public void onComplete(Throwable throwable, Object o) throws Throwable {
        if (throwable == null && o != null) {
          EventBus.getDefault().post(new ChannelRawDataEvent(o.toString()));
        } else {
          EventBus.getDefault().post(new ChannelRawDataEvent(null));
        }
      }
    }, system.dispatcher());
  }

  public void getXpubFromWallet() {
    appKit.electrumWallet.getXpub().onComplete(new OnComplete<ElectrumWallet.GetXpubResponse>() {
      @Override
      public void onComplete(Throwable failure, ElectrumWallet.GetXpubResponse success) throws Throwable {
        if (failure == null && success != null) {
          EventBus.getDefault().post(new XpubEvent(success));
        } else {
          EventBus.getDefault().post(new XpubEvent(null));
        }
      }
    }, system.dispatcher());
  }

  public void checkupInit() {
    if (this.dbHelper == null) {
      this.dbHelper = new DBHelper(getApplicationContext());
    }

    // on-chain balance is initialized with what can be found from the database
    this.onChainBalance.set(package$.MODULE$.millisatoshi2satoshi(new MilliSatoshi(dbHelper.getOnchainBalanceMsat())));

    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    CoinUtils.setCoinPattern(prefs.getString(Constants.SETTING_BTC_PATTERN, getResources().getStringArray(R.array.btc_pattern_values)[2]));
    updateExchangeRate(prefs.getFloat(Constants.SETTING_LAST_KNOWN_RATE_BTC_EUR, 0.0f),
      prefs.getFloat(Constants.SETTING_LAST_KNOWN_RATE_BTC_USD, 0.0f));
  }

  public DBHelper getDBHelper() {
    return dbHelper;
  }

  private static class ExchangeRate {
    private float eurRate;
    private float usdRate;
  }

  public static class AppKit {
    final private ElectrumEclairWallet electrumWallet;
    final private Kit eclairKit;
    final private boolean isDBCompatible;

    public AppKit(final ElectrumEclairWallet wallet, Kit kit, boolean isDBCompatible) {
      this.electrumWallet = wallet;
      this.eclairKit = kit;
      this.isDBCompatible = isDBCompatible;
    }
  }
}
