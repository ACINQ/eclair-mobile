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

import org.bitcoinj.core.Address;
import org.bitcoinj.core.InsufficientMoneyException;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.List;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import akka.util.Timeout;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.Transaction;
import fr.acinq.eclair.DBCompatChecker;
import fr.acinq.eclair.Kit;
import fr.acinq.eclair.Setup;
import fr.acinq.eclair.blockchain.wallet.EclairWallet;
import fr.acinq.eclair.blockchain.wallet.ElectrumWallet;
import fr.acinq.eclair.channel.ChannelEvent;
import fr.acinq.eclair.io.Switchboard;
import fr.acinq.eclair.payment.PaymentEvent;
import fr.acinq.eclair.payment.SendPayment;
import fr.acinq.eclair.router.NetworkEvent;
import fr.acinq.eclair.wallet.events.NotificationEvent;
import fr.acinq.eclair.wallet.events.WalletBalanceUpdateEvent;
import scala.Option;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.Promise;
import scala.concurrent.duration.Duration;

public class App extends Application {

  public final static String TAG = "App";
  public final static String DATADIR_NAME = "eclair-wallet-data";
  private final ActorSystem system = ActorSystem.apply("system");

  private DBHelper dbHelper;
  private ElectrumWallet electrumWallet;
  private ActorRef wallet;
  private ActorRef paymentSupervisor;
  private Kit eclairKit;
  private List<String> mnemonics;

  private Promise<Object> pAtCurrentHeight = akka.dispatch.Futures.promise();
  private boolean isDBCompatible = true;

  @Override
  public void onCreate() {
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this);
    }
    dbHelper = new DBHelper(getApplicationContext());
    try {
      final File datadir = new File(getApplicationContext().getFilesDir(), DATADIR_NAME);
      Log.d(TAG, "Accessing Eclair Setup with datadir " + datadir.getAbsolutePath());

//      EclairBitcoinjKit eclairBitcoinjKit = new EclairBitcoinjKit("test", datadir, this);
//      Future<Wallet> fWallet = eclairBitcoinjKit.getFutureWallet();
//      Future<PeerGroup> fPeerGroup = eclairBitcoinjKit.getFuturePeerGroup();
//      EclairWallet eclairWallet = new BitcoinjWallet(fWallet, system.dispatcher());
//      eclairBitcoinjKit.startAsync();

      Class.forName("org.sqlite.JDBC");
      Setup setup = new Setup(datadir, Option.apply((EclairWallet)null), ConfigFactory.empty(), system);
      ActorRef guiUpdater = system.actorOf(Props.create(EclairEventService.class, dbHelper));
      setup.system().eventStream().subscribe(guiUpdater, ChannelEvent.class);
      setup.system().eventStream().subscribe(guiUpdater, PaymentEvent.class);
      setup.system().eventStream().subscribe(guiUpdater, NetworkEvent.class);
      Future<Kit> fKit = setup.bootstrap();
//      pAtCurrentHeight.completeWith(setup.bitcoin().left().get().atCurrentHeight());
//
//      wallet = Await.result(fWallet, Duration.create(20, "seconds"));
//      peerGroup = Await.result(fPeerGroup, Duration.create(20, "seconds"));
      eclairKit = Await.result(fKit, Duration.create(20, "seconds"));
      pAtCurrentHeight.success(null);
      electrumWallet = (fr.acinq.eclair.blockchain.wallet.ElectrumWallet) eclairKit.wallet();
      wallet = electrumWallet.wallet();
      paymentSupervisor = system.actorOf(Props.create(PaymentSupervisor.class, this, wallet), "payments");
      mnemonics = scala.collection.JavaConverters.seqAsJavaListConverter(Await.result(electrumWallet.getMnemonics(), Duration.create(500, "milliseconds")).toList()).asJava();

      try {
        DBCompatChecker.checkDBCompatibility(setup.nodeParams());
      }
      catch(Exception e) {
        isDBCompatible = false;
      }
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

  public Future<Object> fAtCurrentBlockHeight() {
    return pAtCurrentHeight.future();
  }

  public void publishWalletBalance() {
    Satoshi balance = getWalletBalanceSat();
    EventBus.getDefault().postSticky(new WalletBalanceUpdateEvent(balance));
  }

  public Satoshi getWalletBalanceSat() {
    try {
      return Await.result(electrumWallet.getBalance(), Duration.create(100, "milliseconds"));
    } catch (Exception e) {
      e.printStackTrace();
      return Satoshi.apply(0);
    }
  }

  public void sendLNPayment(int timeout, OnComplete<Object> onComplete, long amountMsat, BinaryData paymentHash, Crypto.PublicKey targetNodeId) {
    Future<Object> paymentFuture = Patterns.ask(
      eclairKit.paymentInitiator(),
      new SendPayment(amountMsat, paymentHash, targetNodeId, 5),
      new Timeout(Duration.create(timeout, "seconds")));
    paymentFuture.onComplete(onComplete, system.dispatcher());
  }

  public void openChannel(int timeout, OnComplete<Object> onComplete,
                          Crypto.PublicKey publicKey, InetSocketAddress address, Switchboard.NewChannel channel) {
    if (publicKey != null && address != null && channel != null) {
      Future<Object> openChannelFuture = Patterns.ask(
        eclairKit.switchboard(),
        new Switchboard.NewConnection(publicKey, address, Option.apply(channel)),
        new Timeout(Duration.create(timeout, "seconds")));
      openChannelFuture.onComplete(onComplete, system.dispatcher());
    }
  }

  public boolean checkAddress(final Address address) {
    return true; // FIXME wallet.getNetworkParameters() == address.getParameters();
  }

  public void sendBitcoinPayment(Satoshi amount, String address) throws InsufficientMoneyException {
    electrumWallet.sendPayment(amount, address);
  }

  public boolean isProduction() {
    return false; // FIXME NetworkParameters.ID_MAINNET.equals(wallet.getNetworkParameters().getId());
  }

  public void broadcastTx(final String payload) {
    final Transaction tx = (Transaction) Transaction.read(payload);
    Future<Object> future = electrumWallet.commit(tx);
    try {
      Boolean success = (Boolean) Await.result(future, Duration.create(500, "milliseconds"));
      if (success)  Log.i(TAG, "Successful broadcast of " + tx.txid());
      else  Log.e(TAG, "cannot broadcast " + tx.txid());
    } catch (Exception e) {
      Log.e(TAG, "Failed broadcast of " + tx.txid(), e);
    }
  }

  public String nodePublicKey() {
    return eclairKit.nodeParams().privateKey().publicKey().toBin().toString();
  }

  public String getWalletPublicAddress() throws Exception {
    return Await.result(electrumWallet.getFinalAddress(), Duration.create(100, "milliseconds"));
  }

  public String getRecoveryPhrase() {
    return Joiner.on(" ").join(mnemonics);
  }

  public boolean checkWordRecoveryPhrase(int position, String word) {
    return mnemonics.get(position).equals(word);
  }

  public DBHelper getDBHelper() {
    return dbHelper;
  }
}


