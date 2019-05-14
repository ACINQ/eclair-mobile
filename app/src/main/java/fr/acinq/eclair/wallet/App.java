/*
 * Copyright 2019 ACINQ SAS
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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import fr.acinq.bitcoin.*;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.Globals;
import fr.acinq.eclair.JsonSerializers$;
import fr.acinq.eclair.Kit;
import fr.acinq.eclair.blockchain.electrum.ElectrumClient;
import fr.acinq.eclair.blockchain.electrum.ElectrumEclairWallet;
import fr.acinq.eclair.blockchain.electrum.ElectrumWallet;
import fr.acinq.eclair.channel.*;
import fr.acinq.eclair.io.Peer;
import fr.acinq.eclair.payment.PaymentLifecycle;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.router.RouteParams;
import fr.acinq.eclair.router.Router;
import fr.acinq.eclair.transactions.Scripts;
import fr.acinq.eclair.wallet.activities.ChannelDetailsActivity;
import fr.acinq.eclair.wallet.activities.LNPaymentDetailsActivity;
import fr.acinq.eclair.wallet.actors.NodeSupervisor;
import fr.acinq.eclair.wallet.adapters.PaymentItemHolder;
import fr.acinq.eclair.wallet.events.*;
import fr.acinq.eclair.wallet.services.CheckElectrumWorker;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import okhttp3.*;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;
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
import scala.math.BigDecimal;
import scodec.bits.ByteVector;
import upickle.default$;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static fr.acinq.eclair.wallet.adapters.LocalChannelItemHolder.EXTRA_CHANNEL_ID;

public class App extends Application {

  public final static Map<String, Float> RATES = new HashMap<>();
  public static @Nullable WalletContext walletContext = null;
  public final ActorSystem system = ActorSystem.apply("system");
  private final Logger log = LoggerFactory.getLogger(App.class);
  public AtomicReference<String> pin = new AtomicReference<>(null);
  public AtomicReference<String> seedHash = new AtomicReference<>(null);
  // version 1 of the backup encryption key uses a m/49' path for derivation, same as BIP49
  public AtomicReference<ByteVector32> backupKey_v1 = new AtomicReference<>(null);
  // version 2 of the backup encryption key uses a m/42'/0' (mainnet) or m/42'/1' (testnet) path, which is better than m/49'.
  // version 1 is kept for backward compatibility
  public AtomicReference<ByteVector32> backupKey_v2 = new AtomicReference<>(null);
  public AppKit appKit;

  private Cancellable pingNode;

  private Cancellable exchangeRatePoller;
  private final OkHttpClient httpClient = new OkHttpClient();

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
    fetchWalletContext();
  }

  /**
   * Triggers a message if the background electrum check worker has not been able to run lately. This should only happen
   * if the device OS force-stops the app and prevents any jobs from running in background.
   * <p>
   * Some devices vendors are known to aggressively kill applications (including background jobs) in order to save battery,
   * unless the app is whitelisted by the user in a custom OS setting page. This behaviour is hard to detect and not
   * standard, and does not happen on a stock android. By checking the timestamp stored in (see
   * {@link Constants#SETTING_ELECTRUM_CHECK_LAST_ATTEMPT_TIMESTAMP}, this mechanism should be able to warn the user if
   * the app is unable to run in background.
   * <p>
   * Note that this mechanism is not fool-proof: the message will not be displayed if the user regularly starts the
   * application (at least once every {@link CheckElectrumWorker#DELAY_BEFORE_BACKGROUND_WARNING}). This however should
   * not be a problem since, in this scenario, the app runs regularly enough so that a background electrum check is in
   * fact not necessary.
   */
  private void detectBackgroundRunnable() {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    final long lastElectrumCheckAttemptDate = prefs.getLong(Constants.SETTING_ELECTRUM_CHECK_LAST_ATTEMPT_TIMESTAMP, System.currentTimeMillis());
    if (System.currentTimeMillis() - lastElectrumCheckAttemptDate > CheckElectrumWorker.DELAY_BEFORE_BACKGROUND_WARNING) {
      log.warn("app has not run in background since {}", DateFormat.getDateTimeInstance().format(new Date(lastElectrumCheckAttemptDate)));
      prefs.edit().putBoolean(Constants.SETTING_BACKGROUND_CANNOT_RUN_WARNING, true).apply();
    } else {
      prefs.edit().putBoolean(Constants.SETTING_BACKGROUND_CANNOT_RUN_WARNING, false).apply();
    }
  }

  public void monitorConnectivity() {
    final ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    final NetworkRequest request = new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
    if (cm != null) {
      final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network n) {
          scheduleConnectionToACINQ();
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
      new PaymentLifecycle.ReceivePayment(amountMsat_opt, description, Option.apply(expiry), NodeSupervisor.getRoutes(), Option.empty()),
      new Timeout(Duration.create(20, "seconds")));
    return (PaymentRequest) Await.result(f, Duration.create(30, "seconds"));
  }

  /**
   * Asks the eclair node to asynchronously execute a Lightning payment. Future failure is silent.
   *
   * @param paymentRequest Lightning payment request
   * @param amountMsat     Amount of the payment in millisatoshis. Overrides the amount provided by the payment request!
   * @param checkFees      True if the user wants to use the default route parameters limiting the route fees to reasonable values.
   *                       If false, can lead the user to pay a lot of fees.
   */
  public void sendLNPayment(final PaymentRequest paymentRequest, final long amountMsat, final boolean checkFees) {
    final Long finalCltvExpiry = paymentRequest.minFinalCltvExpiry().isDefined() && paymentRequest.minFinalCltvExpiry().get() instanceof Long
      ? (Long) paymentRequest.minFinalCltvExpiry().get()
      : (Long) Channel.MIN_CLTV_EXPIRY();

    final Option<RouteParams> routeParams = checkFees
      ? Option.apply(null) // when fee protection is enabled, use the default RouteParams with reasonable values
      : Option.apply(RouteParams.apply( // otherwise, let's build a "no limit" RouteParams
      false, // never randomize on mobile
      package$.MODULE$.millibtc2millisatoshi(new MilliBtc(BigDecimal.exact(1))).amount(), // at most 1mBTC base fee
      1d, // at most 100%
      4,
      Router.DEFAULT_ROUTE_MAX_CLTV(),
      Option.empty()));

    log.info("(lightning) sending {} msat for invoice {}", amountMsat, paymentRequest.toString());
    appKit.eclairKit.paymentInitiator().tell(new PaymentLifecycle.SendPayment(
      amountMsat, paymentRequest.paymentHash(), paymentRequest.nodeId(), paymentRequest.routingInfo(),
      finalCltvExpiry + 1, 10, routeParams), ActorRef.noSender());
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
      final ByteVector placeholderScript = Script.write(Script.pay2wsh(Scripts.multiSig2of2(pubkey, pubkey)));
      final String placeholderAddress = Bech32.encodeWitnessAddress(
        "mainnet".equals(BuildConfig.CHAIN) ? "bc" : "tb",
        (byte) 0,
        Crypto.hash(new SHA256Digest(), placeholderScript));
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

  private boolean hasChannelWithACINQ() {
    final Iterator<HasCommitments> channelsIt = appKit.eclairKit.nodeParams().db().channels().listLocalChannels().iterator();
    while (channelsIt.hasNext()) {
      if (Constants.ACINQ_NODE_URI.nodeId().equals(channelsIt.next().commitments().remoteParams().nodeId())) {
        return true;
      }
    }
    return false;
  }

  private void scheduleConnectionToACINQ() {
    if (pingNode != null) pingNode.cancel();
    if (appKit == null || appKit.eclairKit == null || hasChannelWithACINQ()) return;
    if (system != null) {
      log.info("scheduling connection to ACINQ node");
      pingNode = system.scheduler().schedule(
        Duration.Zero(), Duration.create(10, TimeUnit.MINUTES),
        () -> {
          if (appKit != null && appKit.eclairKit != null && appKit.eclairKit.switchboard() != null) {
            appKit.eclairKit.switchboard().tell(new Peer.Connect(Constants.ACINQ_NODE_URI), ActorRef.noSender());
          }
        },
        system.dispatcher());
    }
  }

  public void fetchWalletContext() {
    httpClient.newCall(new Request.Builder().url(Constants.WALLET_CONTEXT_SOURCE).build()).enqueue(new Callback() {
      @Override
      public void onFailure(@NonNull Call call, @NonNull IOException e) {
        log.warn("could not retrieve wallet context from acinq, defaulting to fallback");
      }

      @Override
      public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
        final ResponseBody body = response.body();
        if (response.isSuccessful() && body != null) {
          try {
            final JSONObject json = new JSONObject(body.string());
            log.debug("wallet context responded with {}", json.toString(2));
            final int latestAppCode = json.getJSONObject(BuildConfig.CHAIN).getInt("version");
            final double liquidityPrice = json.getJSONObject(BuildConfig.CHAIN).getJSONObject("liquidity").getJSONObject("v1").getDouble("price");
            walletContext = new WalletContext(latestAppCode, liquidityPrice);
          } catch (JSONException e) {
            log.error("could not read wallet context body", e);
          }
        } else {
          log.warn("wallet context query responds with code {}, defaulting to fallback", response.code());
        }
      }
    });
  }

  public void scheduleExchangeRatePoll() {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    if (exchangeRatePoller != null) {
      exchangeRatePoller.cancel();
    }
    if (system != null) {
      exchangeRatePoller = system.scheduler().schedule(
        Duration.Zero(), Duration.create(20, TimeUnit.MINUTES),
        () -> httpClient.newCall(new Request.Builder().url(Constants.PRICE_RATE_API).build()).enqueue(new Callback() {
          @Override
          public void onFailure(@NonNull Call call, @NonNull IOException e) {
            log.warn("exchange rate call failed with cause {}", e.getLocalizedMessage());
          }

          @Override
          public void onResponse(@NonNull Call call, @NonNull Response response) {
            log.debug("exchange rate api responded with {}", response);
            if (!response.isSuccessful()) {
              log.warn("exchange rate query responds with error code {}", response.code());
            } else {
              final ResponseBody body = response.body();
              if (body != null) {
                try {
                  WalletUtils.handleExchangeRateResponse(prefs, body);
                } catch (Throwable t) {
                  log.error("could not read exchange rate response body", t);
                } finally {
                  body.close();
                }
              } else {
                log.warn("exchange rate body is null");
              }
            }
          }
        }),
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
    return appKit.eclairKit.nodeParams().privateKey().publicKey().toString();
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
        if (throwable == null && o instanceof Iterable) {
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
  public void getLocalChannelRawData(final ByteVector32 channelId) {
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

  public static class WalletContext {
    public final int version;
    public final double liquidityRate;

    public WalletContext(final int version, final double liquidityRate) {
      this.version = version;
      this.liquidityRate = liquidityRate;
    }
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
