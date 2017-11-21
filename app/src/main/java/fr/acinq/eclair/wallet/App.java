package fr.acinq.eclair.wallet;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.common.base.Joiner;
import com.typesafe.config.ConfigFactory;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import akka.util.Timeout;
import fr.acinq.bitcoin.Base58;
import fr.acinq.bitcoin.Base58Check;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.Transaction;
import fr.acinq.eclair.DBCompatChecker;
import fr.acinq.eclair.Globals;
import fr.acinq.eclair.Kit;
import fr.acinq.eclair.Setup;
import fr.acinq.eclair.blockchain.EclairWallet;
import fr.acinq.eclair.blockchain.electrum.ElectrumEclairWallet;
import fr.acinq.eclair.blockchain.electrum.ElectrumWallet;
import fr.acinq.eclair.channel.ChannelEvent;
import fr.acinq.eclair.io.Switchboard;
import fr.acinq.eclair.payment.PaymentEvent;
import fr.acinq.eclair.payment.SendPayment;
import fr.acinq.eclair.router.NetworkEvent;
import fr.acinq.eclair.wallet.events.NetworkChannelsCountEvent;
import fr.acinq.eclair.wallet.events.NetworkNodesCountEvent;
import fr.acinq.eclair.wallet.events.NotificationEvent;
import fr.acinq.eclair.wallet.utils.EclairStartException;
import scala.Option;
import scala.Symbol;
import scala.collection.Iterable;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.Promise;
import scala.concurrent.duration.Duration;

public class App extends Application {

  public final static String TAG = "App";
  public final static String DATADIR_NAME = "eclair-wallet-data";
  private final static ExchangeRate exchangeRate = new ExchangeRate();
  public final ActorSystem system = ActorSystem.apply("system");
  private final Promise<Object> pAtCurrentHeight = akka.dispatch.Futures.promise();
  public AtomicReference<Satoshi> onChainBalance = new AtomicReference<>(new Satoshi(0));
  private DBHelper dbHelper;
  private ElectrumEclairWallet electrumWallet;
  private ActorRef wallet;
  private Kit eclairKit;
  private boolean isDBCompatible = true;
  private String walletAddress = "N/A";

  /**
   * Update the application's exchange rate in BTCUSD and BTCEUR.
   *
   * @param eurRate value of 1 BTC in EURO
   * @param usdRate value of 1 BTC in USD
   */
  public static void updateExchangeRate(final Double eurRate, final Double usdRate) {
    exchangeRate.eurRate = eurRate;
    exchangeRate.usdRate = usdRate;
  }

  /**
   * Returns the value of 1 BTC in EURO.
   *
   * @return
   */
  public static Double getEurRate() {
    return exchangeRate.eurRate;
  }

  /**
   * Returns the value of 1 BTC in USD.
   *
   * @return
   */
  public static Double getUsdRate() {
    return exchangeRate.usdRate;
  }

  @Override
  public void onCreate() {
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this);
    }
    dbHelper = new DBHelper(getApplicationContext());

    try {
      final File datadir = new File(getApplicationContext().getFilesDir(), DATADIR_NAME);
      Log.d(TAG, "Accessing Eclair Setup with datadir " + datadir.getAbsolutePath());

      Class.forName("org.sqlite.JDBC");
      Setup setup = new Setup(datadir, Option.apply((EclairWallet) null), ConfigFactory.empty(), system);
      ActorRef guiUpdater = system.actorOf(Props.create(EclairEventService.class, dbHelper));
      setup.system().eventStream().subscribe(guiUpdater, ChannelEvent.class);
      setup.system().eventStream().subscribe(guiUpdater, PaymentEvent.class);
      setup.system().eventStream().subscribe(guiUpdater, NetworkEvent.class);
      Future<Kit> fKit = setup.bootstrap();
      eclairKit = Await.result(fKit, Duration.create(20, "seconds"));
      pAtCurrentHeight.success(null);
      electrumWallet = (fr.acinq.eclair.blockchain.electrum.ElectrumEclairWallet) eclairKit.wallet();
      wallet = electrumWallet.wallet();
      system.actorOf(Props.create(PaymentSupervisor.class, this, wallet), "payments");

      try {
        DBCompatChecker.checkDBCompatibility(setup.nodeParams());
      } catch (Exception e) {
        isDBCompatible = false;
      }
      Log.i(TAG, "Wallet started, App.onCreate done");
    } catch (Exception e) {
      Log.e(TAG, "Failed to start eclair", e);
      // the wallet must crash at this point
      throw new EclairStartException();
    }

    super.onCreate();
  }

  /**
   * Returns true if the wallet is not compatible with the local datas.
   *
   * @return
   */
  public boolean hasBreakingChanges() {
    return !isDBCompatible;
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
    return "";
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleNotification(NotificationEvent notificationEvent) {
    NotificationCompat.Builder notification = new NotificationCompat.Builder(this.getBaseContext())
      .setVisibility(NotificationCompat.VISIBILITY_SECRET)
      .setPriority(Notification.PRIORITY_HIGH)
      .setDefaults(Notification.DEFAULT_SOUND)
      .setVibrate(new long[]{0l})
      .setSmallIcon(R.drawable.eclair_256x256)
      .setContentTitle(notificationEvent.title)
      .setContentText(notificationEvent.message)
      .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationEvent.bigMessage));
    NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    mNotificationManager.notify(notificationEvent.tag, notificationEvent.id, notification.build());
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleNewWalletAddreess(final ElectrumWallet.NewWalletReceiveAddress addressEvent) {
    walletAddress = addressEvent.address();
  }

  public String getWalletAddress() {
    return this.walletAddress;
  }

  public Future<Object> fAtCurrentBlockHeight() {
    return pAtCurrentHeight.future();
  }

  /**
   * Asks the eclair node to asynchronously execute a Lightning payment. Completes with a
   * {@link akka.pattern.AskTimeoutException} after the timeout has expired.
   *
   * @param timeout     timeout in milliseconds
   * @param onComplete  Callback executed once the future completes (with success or failure)
   * @param amountMsat  Amount of the payment in millisatoshis
   * @param paymentHash Hash of the payment preimage
   * @param publicKey   public key of the recipient node
   */
  public void sendLNPayment(final int timeout, final OnComplete<Object> onComplete, final long amountMsat,
                            final BinaryData paymentHash, final Crypto.PublicKey publicKey, final int minFinalCltvExpiry) {
    Future<Object> paymentFuture = Patterns.ask(
      eclairKit.paymentInitiator(),
      new SendPayment(amountMsat, paymentHash, publicKey, minFinalCltvExpiry, 5),
      new Timeout(Duration.create(timeout, "seconds")));
    paymentFuture.onComplete(onComplete, system.dispatcher());
  }

  /**
   * Asks the eclair node to asynchronously open a channel with a node. Completes with a
   * {@link akka.pattern.AskTimeoutException} after the timeout has expired.
   *
   * @param timeout    in milliseconds
   * @param onComplete Callback executed once the future completes (with success or failure)
   * @param publicKey  public key of the node
   * @param address    ip:port of the node
   * @param channel    channel to create, contains the capacity of the channel, in satoshis
   */
  public void openChannel(final int timeout, final OnComplete<Object> onComplete,
                          final Crypto.PublicKey publicKey, final InetSocketAddress address, final Switchboard.NewChannel channel) {
    if (publicKey != null && address != null && channel != null) {
      Future<Object> openChannelFuture = Patterns.ask(
        eclairKit.switchboard(),
        new Switchboard.NewConnection(publicKey, address, Option.apply(channel)),
        new Timeout(Duration.create(timeout, "seconds")));
      openChannelFuture.onComplete(onComplete, system.dispatcher());
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
      Log.e(TAG, "Could not check address parameter", t);
    }
    return false;
  }

  public ElectrumEclairWallet getWallet() {
    return electrumWallet;
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
    Future<Object> future = electrumWallet.commit(tx);
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
    return eclairKit.nodeParams().privateKey().publicKey().toBin().toString();
  }

  /**
   * Returns the wallet's recovery phrase. Throws an exception if the wallet does not answer after 2000ms.
   *
   * @return
   * @throws Exception
   */
  public String getRecoveryPhrase() throws Exception {
    final List<String> mnemonics = scala.collection.JavaConverters.seqAsJavaListConverter(
      Await.result(electrumWallet.getMnemonics(), Duration.create(2000, "milliseconds")).toList()).asJava();
    return Joiner.on(" ").join(mnemonics);
  }

  /**
   * Checks if the word belongs to the recovery phrase and is at the right position.
   *
   * @param position position of the word in the recovery phrase
   * @param word     word in the recovery phrase
   * @return false if the check fails
   */
  public boolean checkWordRecoveryPhrase(int position, String word) throws Exception {
    final List<String> mnemonics = scala.collection.JavaConverters.seqAsJavaListConverter(
      Await.result(electrumWallet.getMnemonics(), Duration.create(2000, "milliseconds")).toList()).asJava();
    return mnemonics.get(position).equals(word);
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
   * Asynchronously asks for the Lightning Network's nodes count. Dispatch a {@link NetworkNodesCountEvent} containing the nodes count.
   * The call timeouts fails after 10 seconds. When the call fails, the network's nodes count will be -1.
   */
  public void getNetworkNodesCount() {
    Future<Object> paymentFuture = Patterns.ask(eclairKit.router(), Symbol.apply("nodes"), new Timeout(Duration.create(10, "seconds")));
    paymentFuture.onComplete(new OnComplete<Object>() {
      @Override
      public void onComplete(Throwable throwable, Object o) throws Throwable {
        if (throwable == null && o != null && o instanceof Iterable) {
          EventBus.getDefault().post(new NetworkNodesCountEvent(((Iterable) o).size()));
        } else {
          EventBus.getDefault().post(new NetworkNodesCountEvent(-1));
        }
      }
    }, system.dispatcher());
  }

  /**
   * Asynchronously asks for the Lightning Network's channels count. Dispatch a {@link NetworkChannelsCountEvent} containing the channel count.
   * The call timeouts fails after 10 seconds. When the call fails, the network's channels count will be -1.
   */
  public void getNetworkChannelsCount() {
    Future<Object> paymentFuture = Patterns.ask(eclairKit.router(), Symbol.apply("channels"), new Timeout(Duration.create(10, "seconds")));
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

  public DBHelper getDBHelper() {
    return dbHelper;
  }

  private static class ExchangeRate {
    private Double eurRate = 0.0;
    private Double usdRate = 0.0;
  }
}


