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

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.dispatch.OnComplete;
import akka.dispatch.OnFailure;
import akka.pattern.Patterns;
import akka.util.Timeout;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.text.format.DateUtils;
import fr.acinq.bitcoin.*;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.Globals;
import fr.acinq.eclair.JsonSerializers$;
import fr.acinq.eclair.Kit;
import fr.acinq.eclair.blockchain.electrum.ElectrumClient;
import fr.acinq.eclair.blockchain.electrum.ElectrumEclairWallet;
import fr.acinq.eclair.blockchain.electrum.ElectrumWallet;
import fr.acinq.eclair.channel.CMD_GETINFO$;
import fr.acinq.eclair.channel.Channel;
import fr.acinq.eclair.channel.RES_GETINFO;
import fr.acinq.eclair.channel.Register;
import fr.acinq.eclair.io.Peer;
import fr.acinq.eclair.payment.PaymentLifecycle;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.transactions.Scripts;
import fr.acinq.eclair.wallet.activities.ChannelDetailsActivity;
import fr.acinq.eclair.wallet.activities.LNPaymentDetailsActivity;
import fr.acinq.eclair.wallet.actors.NodeSupervisor;
import fr.acinq.eclair.wallet.adapters.PaymentItemHolder;
import fr.acinq.eclair.wallet.events.*;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.digests.SHA256Digest;
import scala.Option;
import scala.Symbol;
import scala.Tuple2;
import scala.collection.Iterable;
import scala.collection.Iterator;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import upickle.default$;

import java.net.InetSocketAddress;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static fr.acinq.eclair.wallet.adapters.LocalChannelItemHolder.EXTRA_CHANNEL_ID;

public class App extends Application {

  public final static Map<String, Float> RATES = new HashMap<>();
  public final ActorSystem system = ActorSystem.apply("system");
  private final Logger log = LoggerFactory.getLogger(App.class);
  public AtomicReference<String> pin = new AtomicReference<>(null);
  public AtomicReference<String> seedHash = new AtomicReference<>(null);
  // version 1 of the backup encryption key uses a m/49' path for derivation, same as BIP49
  public AtomicReference<BinaryData> backupKey_v1 = new AtomicReference<>(null);
  // version 2 of the backup encryption key uses a m/42'/0' (mainnet) or m/42'/1' (testnet) path, which is better than m/49'.
  // version 1 is kept for backward compatibility
  public AtomicReference<BinaryData> backupKey_v2 = new AtomicReference<>(null);
  public AppKit appKit;
  private Cancellable pingNode;
  private AtomicReference<ElectrumState> electrumState = new AtomicReference<>(null);
  private DBHelper dbHelper;

  @Override
  public void onCreate() {
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this);
    }
    super.onCreate();
    WalletUtils.setupLogging(getBaseContext());
    detectBackgroundRunnable();
  }

  private void detectBackgroundRunnable() {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    final long lastStartDate = prefs.getLong(Constants.SETTING_ELECTRUM_CHECK_LAST_DATE, System.currentTimeMillis());
    if (lastStartDate > 0 && System.currentTimeMillis() - lastStartDate > DateUtils.MINUTE_IN_MILLIS * 3) {
      log.warn("app has not run in background since {}", DateFormat.getDateTimeInstance().format(new Date(lastStartDate)));
      prefs.edit().putBoolean(Constants.SETTING_BACKGROUND_DISABLED_WARNING, true).apply();
    }
  }

  public void monitorConnectivity() {
    final ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    final NetworkRequest request = new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
    if (cm != null) {
      final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network n) {
          scheduleConnectionToNode();
          // TODO: reconnect electrum
        }
      };
      cm.registerNetworkCallback(request, callback);
    }
  }

  /**
   * Return application's version
   */
  public String getVersion() {
    try {
      return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
    } catch (PackageManager.NameNotFoundException e) {
    }
    return "N/A";
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleNotification(ClosingChannelNotificationEvent event) {
    final String notifTitle = getString(R.string.notif_channelclosing_title, event.remoteNodeId);
    final String notifMessage = getString(R.string.notif_channelclosing_message, CoinUtils.formatAmountInUnit(event.balanceAtClosing, CoinUtils.getUnitFromString("btc"), true));
    final StringBuilder notifBigMessage = new StringBuilder().append(notifMessage).append("\n")
      .append(getString(R.string.notif_channelclosing_bigmessage, event.channelId.substring(0, 12) + "...")).append("\n");
    if (event.isLocalClosing) {
      notifBigMessage.append(getString(R.string.notif_channelclosing_bigmessage_localclosing, event.toSelfDelay));
    } else {
      notifBigMessage.append(getString(R.string.notif_channelclosing_bigmessage_normal));
    }
    final Intent intent = new Intent(this, ChannelDetailsActivity.class);
    intent.putExtra(EXTRA_CHANNEL_ID, event.channelId);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

    final NotificationCompat.Builder builder = new NotificationCompat.Builder(this.getBaseContext(), Constants.NOTIF_CHANNEL_CLOSED_ID)
      .setSmallIcon(R.drawable.eclair_256x256)
      .setContentTitle(notifTitle)
      .setContentText(notifMessage)
      .setStyle(new NotificationCompat.BigTextStyle().bigText(notifBigMessage.toString()))
      .setContentIntent(PendingIntent.getActivity(this, (int) (System.currentTimeMillis() & 0xfffffff), intent, PendingIntent.FLAG_UPDATE_CURRENT))
      .setAutoCancel(true);

    NotificationManagerCompat.from(this).notify((int) (System.currentTimeMillis() & 0xfffffff), builder.build());
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleNotification(final ReceivedLNPaymentNotificationEvent event) {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

    final String notifTitle = getString(R.string.notif_received_ln_payment_title, CoinUtils.formatAmountInUnit(event.amount, WalletUtils.getPreferredCoinUnit(prefs), true));
    final String notifMessage = getString(R.string.notif_received_ln_payment_message, event.paymentDescription);

    final Intent intent = new Intent(this, LNPaymentDetailsActivity.class);
    intent.putExtra(PaymentItemHolder.EXTRA_PAYMENT_ID, event.paymentHash);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

    final NotificationCompat.Builder builder = new NotificationCompat.Builder(this.getBaseContext(), Constants.NOTIF_CHANNEL_RECEIVED_LN_PAYMENT_ID)
      .setSmallIcon(R.drawable.eclair_256x256)
      .setContentTitle(notifTitle)
      .setContentText(notifMessage)
      .setContentIntent(PendingIntent.getActivity(this, (int) (System.currentTimeMillis() & 0xfffffff), intent, PendingIntent.FLAG_UPDATE_CURRENT))
      .setAutoCancel(true);

    NotificationManagerCompat.from(this).notify((int) (System.currentTimeMillis() & 0xfffffff), builder.build());
  }

  @Subscribe(threadMode = ThreadMode.POSTING, sticky = true)
  public void handleNewWalletAddress(final ElectrumWallet.NewWalletReceiveAddress addressEvent) {
    if (electrumState.get() != null) {
      electrumState.get().onchainAddress = addressEvent.address();
    }
  }

  @Subscribe(threadMode = ThreadMode.POSTING)
  public void handleWalletReadyEvent(ElectrumWallet.WalletReady event) {
    final ElectrumState state = this.electrumState.get() == null ? new ElectrumState() : this.electrumState.get();
    state.confirmedBalance = event.confirmedBalance();
    state.unconfirmedBalance = event.unconfirmedBalance();
    state.blockTimestamp = event.timestamp();
    state.isConnected = true;
    this.electrumState.set(state);
  }

  @Subscribe(threadMode = ThreadMode.POSTING)
  public void handleWalletDisconnectedEvent(ElectrumClient.ElectrumDisconnected$ event) {
    final ElectrumState state = this.electrumState.get() == null ? new ElectrumState() : this.electrumState.get();
    state.isConnected = false;
    this.electrumState.set(state);
  }

  @Subscribe(threadMode = ThreadMode.POSTING)
  public void handleElectrumReadyEvent(ElectrumClient.ElectrumReady event) {
    final ElectrumState state = this.electrumState.get() == null ? new ElectrumState() : this.electrumState.get();
    state.address = event.serverAddress();
    this.electrumState.set(state);
  }

  /**
   * Generates a payment request. Uses a blocking await so must *not* be called from an UI thread. Fails after 20 secs.
   */
  public PaymentRequest generatePaymentRequest(final String description, final Option<MilliSatoshi> amountMsat_opt, final long expiry) throws Exception {
    Future<Object> f = Patterns.ask(appKit.eclairKit.paymentHandler(),
      new PaymentLifecycle.ReceivePayment(amountMsat_opt, description, Option.apply(expiry), NodeSupervisor.getRoutes()),
      new Timeout(Duration.create(20, "seconds")));
    return (PaymentRequest) Await.result(f, Duration.create(30, "seconds"));
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
    Patterns.ask(appKit.eclairKit.paymentInitiator(),
      new PaymentLifecycle.SendPayment(amountMsat, paymentRequest.paymentHash(), paymentRequest.nodeId(), paymentRequest.routingInfo(),
        finalCltvExpiry + 1, 10, Option.apply(null), Option.apply(null)),
      new Timeout(Duration.create(1, "seconds"))).onFailure(new OnFailure() {
      @Override
      public void onFailure(Throwable failure) throws Throwable {
      }
    }, this.system.dispatcher());
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
          if (t != null) {
            log.warn("could not send bitcoin tx {} with cause {}", txId, t.getMessage());
            EventBus.getDefault().post(new BitcoinPaymentFailedEvent(t.getLocalizedMessage()));
          }
        }
      }, this.system.dispatcher());
    } catch (Throwable t) {
      log.warn("could not send bitcoin tx with cause {}", t.getMessage());
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
              final Future fSendAll = appKit.electrumWallet.commit(res._1());
              fSendAll.onComplete(new OnComplete<Boolean>() {
                @Override
                public void onComplete(Throwable failure, Boolean success) {
                  if (failure != null) {
                    log.warn("error in send_all tx", failure);
                    EventBus.getDefault().post(new BitcoinPaymentFailedEvent(failure.getLocalizedMessage()));
                  } else if (success == null || !success) {
                    log.warn("send_all tx has failed");
                    EventBus.getDefault().post(new BitcoinPaymentFailedEvent(getString(R.string.payment_tx_failed)));
                  }
                }
              }, system.dispatcher());
            } else {
              log.warn("could not create send all tx");
              EventBus.getDefault().post(new BitcoinPaymentFailedEvent(getString(R.string.payment_tx_creation_error)));
            }
          } else {
            log.warn("could not send all balance with cause {}", t.getMessage());
            EventBus.getDefault().post(new BitcoinPaymentFailedEvent(t.getLocalizedMessage()));
          }
        }
      }, this.system.dispatcher());
    } catch (Throwable t) {
      log.warn("could not send send all balance with cause {}", t.getMessage());
      EventBus.getDefault().post(new BitcoinPaymentFailedEvent(t.getLocalizedMessage()));
    }
  }

  /**
   * Retrieve all the available on-chain funds after a given network fees for a multisig tx.
   */
  public Satoshi getAvailableFundsAfterFees(final long feesPerKw) {
    try {
      // simulate multisig transaction to our self to retrieve outputs total amount
      final Crypto.PublicKey pubkey = appKit.eclairKit.nodeParams().privateKey().publicKey();
      final BinaryData placeholderScript = Script.write(Script.pay2wsh(Scripts.multiSig2of2(pubkey, pubkey)));
      final String placeholderAddress = Bech32.encodeWitnessAddress("mainnet".equals(BuildConfig.CHAIN) ? "bc" : "tb", (byte) 0, Crypto.hash(new SHA256Digest(), package$.MODULE$.binaryData2Seq(placeholderScript)));
      final Tuple2<Transaction, Satoshi> tx_fee = Await.result(appKit.electrumWallet.sendAll(placeholderAddress, feesPerKw), Duration.create(20, "seconds"));
      long available = 0;
      Iterator<TxOut> it = tx_fee._1.txOut().iterator();
      while (it.hasNext()) {
        available += it.next().amount().amount();
      }
      available -= tx_fee._2.amount();
      return new Satoshi(Math.max(0, available));
    } catch (Exception e) {
      log.error("could not retrieve max available funds after fees", e);
      return new Satoshi(0);
    }
  }

  public void scheduleConnectionToNode() {
    if (pingNode != null) pingNode.cancel();
    if (system != null && appKit != null && appKit.eclairKit != null && appKit.eclairKit.switchboard() != null) {
      pingNode = system.scheduler().schedule(
        Duration.Zero(), Duration.create(60, "seconds"),
        () -> appKit.eclairKit.switchboard().tell(new Peer.Connect(WalletUtils.ACINQ_NODE), ActorRef.noSender()),
        system.dispatcher());
    }
  }

  /**
   * Broadcast a transaction using the payload.
   */
  public void broadcastTx(final String payload) {
    final Transaction tx = (Transaction) Transaction.read(payload);
    Future<Object> future = appKit.electrumWallet.commit(tx);
    try {
      Await.result(future, Duration.create(500, "milliseconds"));
    } catch (Exception e) {
      log.warn("failed broadcast of tx {}", tx.txid(), e);
    }
  }

  /**
   * Returns the eclair node's public key.
   */
  public String nodePublicKey() {
    return appKit.eclairKit.nodeParams().privateKey().publicKey().toBin().toString();
  }

  public static long estimateSlowFees() {
    return Math.max(Globals.feeratesPerKB().get().blocks_72() / 1000, 1);
  }

  public static long estimateMediumFees() {
    return Math.max(Globals.feeratesPerKB().get().blocks_12() / 1000, estimateSlowFees());
  }

  public static long estimateFastFees() {
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
    }, this.system.dispatcher());
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
          RES_GETINFO result = (RES_GETINFO) o;
          String json = default$.MODULE$.write(result, 1, JsonSerializers$.MODULE$.cmdResGetinfoReadWriter());
          EventBus.getDefault().post(new ChannelRawDataEvent(json));
        } else {
          EventBus.getDefault().post(new ChannelRawDataEvent(null));
        }
      }
    }, this.system.dispatcher());
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
    }, this.system.dispatcher());
  }

  public void checkupInit() {
    if (this.dbHelper == null) {
      this.dbHelper = new DBHelper(getApplicationContext());
      this.dbHelper.cleanLightningPayments();
    }

    // delete unconfirmed onchain txs to get a clean slate before connecting to an electrum server
    dbHelper.cleanUpZeroConfs();

    // rates & coin patterns
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    WalletUtils.retrieveRatesFromPrefs(prefs);
    CoinUtils.setCoinPattern(prefs.getString(Constants.SETTING_BTC_PATTERN, getResources().getStringArray(R.array.btc_pattern_values)[3]));

    // notification channels (android 8+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      final NotificationChannel lightningChannelClosing = new NotificationChannel(Constants.NOTIF_CHANNEL_CLOSED_ID,
        getString(R.string.notification_channel_closing_ln_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
      lightningChannelClosing.setDescription(getString(R.string.notification_channel_closing_ln_channel_desc));
      final NotificationChannel startAppReminder = new NotificationChannel(Constants.NOTIF_CHANNEL_START_REMINDER_ID,
        getString(R.string.notification_channel_restart_name), NotificationManager.IMPORTANCE_HIGH);
      startAppReminder.setDescription(getString(R.string.notification_channel_restart_desc));
      final NotificationChannel receivedLNPayment = new NotificationChannel(Constants.NOTIF_CHANNEL_RECEIVED_LN_PAYMENT_ID,
        getString(R.string.notification_channel_received_ln_payment_name), NotificationManager.IMPORTANCE_DEFAULT);
      startAppReminder.setDescription(getString(R.string.notification_channel_received_ln_payment_desc));
      // Register the channel with the system
      final NotificationManager notificationManager = getSystemService(NotificationManager.class);
      if (notificationManager != null) {
        notificationManager.createNotificationChannel(lightningChannelClosing);
        notificationManager.createNotificationChannel(startAppReminder);
        notificationManager.createNotificationChannel(receivedLNPayment);
      }
    }
  }

  public Satoshi getOnchainBalance() {
    // if electrum has not send any data, fetch last known onchain balance from DB
    if (this.electrumState.get() == null
      || this.electrumState.get().confirmedBalance == null || this.electrumState.get().unconfirmedBalance == null) {
      return package$.MODULE$.millisatoshi2satoshi(new MilliSatoshi(dbHelper.getOnchainBalanceMsat()));
    } else {
      final Satoshi confirmed = electrumState.get().confirmedBalance;
      final Satoshi unconfirmed = electrumState.get().unconfirmedBalance;
      return confirmed.$plus(unconfirmed);
    }
  }

  public long getBlockTimestamp() {
    return this.electrumState.get() == null ? 0 : this.electrumState.get().blockTimestamp;
  }

  public ElectrumState getElectrumState() {
    return this.electrumState.get();
  }

  public InetSocketAddress getElectrumServerAddress() {
    return this.electrumState.get() == null ? null : this.electrumState.get().address;
  }

  public DBHelper getDBHelper() {
    return dbHelper;
  }

  public static class ElectrumState {
    public Satoshi confirmedBalance;
    public Satoshi unconfirmedBalance;
    public long blockTimestamp;
    public InetSocketAddress address;
    public boolean isConnected = false;
    public String onchainAddress = null;
  }

  public static class AppKit {
    final public Kit eclairKit;
    final private ElectrumEclairWallet electrumWallet;

    public AppKit(final ElectrumEclairWallet wallet, final Kit kit) {
      this.electrumWallet = wallet;
      this.eclairKit = kit;
    }
  }
}
