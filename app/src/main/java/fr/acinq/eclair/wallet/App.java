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
import fr.acinq.bitcoin.Base58;
import fr.acinq.bitcoin.Base58Check;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.Transaction;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.Globals;
import fr.acinq.eclair.Kit;
import fr.acinq.eclair.blockchain.electrum.ElectrumEclairWallet;
import fr.acinq.eclair.blockchain.electrum.ElectrumWallet;
import fr.acinq.eclair.io.NodeURI;
import fr.acinq.eclair.io.Peer;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.payment.SendPayment;
import fr.acinq.eclair.wallet.events.BitcoinPaymentFailedEvent;
import fr.acinq.eclair.wallet.events.LNNewChannelFailureEvent;
import fr.acinq.eclair.wallet.events.NetworkChannelsCountEvent;
import fr.acinq.eclair.wallet.events.NotificationEvent;
import fr.acinq.eclair.wallet.events.WalletStateUpdateEvent;
import fr.acinq.eclair.wallet.utils.Constants;
import scala.Symbol;
import scala.collection.Iterable;
import scala.collection.Seq$;
import scala.collection.immutable.Seq;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

public class App extends Application {

  public final static String TAG = "App";
  private final static ExchangeRate exchangeRate = new ExchangeRate();
  public final ActorSystem system = ActorSystem.apply("system");
  public AtomicReference<Satoshi> onChainBalance = new AtomicReference<>(new Satoshi(0));
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
   * @param amountMsat      Amount of the payment in millisatoshis
   * @param paymentHash     Hash of the payment preimage
   * @param publicKey       Public key of the recipient node
   * @param finalCltvExpiry Expiry of the payment, in blocks
   */
  public void sendLNPayment(final long amountMsat, final BinaryData paymentHash, final Crypto.PublicKey publicKey, final Long finalCltvExpiry) {
    Patterns.ask(appKit.eclairKit.paymentInitiator(),
      new SendPayment(amountMsat, paymentHash, publicKey, (Seq<scala.collection.Seq<PaymentRequest.ExtraHop>>) Seq$.MODULE$.empty(), finalCltvExpiry, 20),
      new Timeout(Duration.create(1, "seconds"))).onFailure(new OnFailure() {
      @Override
      public void onFailure(Throwable failure) throws Throwable {}
    }, system.dispatcher());
  }

  /**
   * Execute an onchain transaction with electrum.
   *
   * @param amountSat amount to send in satoshis
   * @param address   recipient of the tx
   * @param feesPerKw fees for the tx
   */
  public void sendBitcoinPayment(final Satoshi amountSat, final String address, final long feesPerKw) {
    try {
      Future fBitcoinPayment = appKit.electrumWallet.sendPayment(amountSat, address, feesPerKw);
      fBitcoinPayment.onComplete(new OnComplete<String>() {
        @Override
        public void onComplete(final Throwable t, final String txId) {
          if (t == null) {
            Log.i(TAG, "Successfully sent tx " + txId);
          } else {
            Log.e(TAG, "Bitcoin tx has failed ", t);
            EventBus.getDefault().post(new BitcoinPaymentFailedEvent(t.getLocalizedMessage()));
          }
        }
      }, this.system.dispatcher());
    } catch (Throwable t) {
      Log.e(TAG, "Could not send Bitcoin tx ", t);
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
   * Checks if the bitcoin address parameters match with the wallet's chain.
   *
   * @param address bitcoin public address
   * @return false if address chain does not match the wallet's.
   */
  public boolean checkAddressParameters(final String address) {
    try {
      Object byte1 = Base58Check.decode(address)._1();
      boolean isTestNet = byte1.equals(Base58.Prefix$.MODULE$.PubkeyAddressTestnet()) || byte1.equals(Base58.Prefix$.MODULE$.ScriptAddressTestnet());
      boolean isMainNet = byte1.equals(Base58.Prefix$.MODULE$.PubkeyAddress()) || byte1.equals(Base58.Prefix$.MODULE$.ScriptAddress());
      return isTestNet;
    } catch (Throwable t) {
      Log.e(TAG, "Could not check address parameter for address=" + address, t);
    }
    return false;
  }

  public boolean isProduction() {
    return false; // FIXME NetworkParameters.ID_MAINNET.equals(wallet.getNetworkParameters().getId());
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
      if (success) Log.i(TAG, "Successful broadcast of " + tx.txid());
      else Log.e(TAG, "cannot broadcast " + tx.txid());
    } catch (Exception e) {
      Log.e(TAG, "Failed broadcast of " + tx.txid(), e);
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
    return Globals.feeratesPerByte().get().blocks_72();
  }

  public long estimateMediumFees() {
    return Globals.feeratesPerByte().get().blocks_12();
  }

  public long estimateFastFees() {
    return Globals.feeratesPerByte().get().blocks_2();
  }

  /**
   * Asynchronously asks for the Lightning Network's channels count. Dispatch a {@link NetworkChannelsCountEvent} containing the channel count.
   * The call timeouts fails after 10 seconds. When the call fails, the network's channels count will be -1.
   */
  public void getNetworkChannelsCount() {
    Future<Object> paymentFuture = Patterns.ask(appKit.eclairKit.router(), Symbol.apply("channels"), new Timeout(Duration.create(10, "seconds")));
    paymentFuture.onComplete(new OnComplete<Object>() {
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
