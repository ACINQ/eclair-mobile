package fr.acinq.eclair.wallet;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.common.base.Joiner;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.typesafe.config.ConfigFactory;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.KeyChain;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.spongycastle.util.encoders.Hex;

import java.io.File;
import java.net.InetSocketAddress;

import javax.annotation.Nullable;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import akka.util.Timeout;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.eclair.DBCompatChecker;
import fr.acinq.eclair.Kit;
import fr.acinq.eclair.Setup;
import fr.acinq.eclair.blockchain.wallet.BitcoinjWallet;
import fr.acinq.eclair.blockchain.wallet.EclairWallet;
import fr.acinq.eclair.channel.ChannelEvent;
import fr.acinq.eclair.io.Switchboard;
import fr.acinq.eclair.payment.PaymentEvent;
import fr.acinq.eclair.payment.SendPayment;
import fr.acinq.eclair.router.NetworkEvent;
import fr.acinq.eclair.wallet.events.NetworkChannelsCountEvent;
import fr.acinq.eclair.wallet.events.NetworkNodesCountEvent;
import fr.acinq.eclair.wallet.events.NotificationEvent;
import fr.acinq.eclair.wallet.events.WalletBalanceUpdateEvent;
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
  private final ActorSystem system = ActorSystem.apply("system");

  private DBHelper dbHelper;
  private PeerGroup peerGroup;
  private Wallet wallet;
  private Kit eclairKit;

  private Promise<Object> pAtCurrentHeight = akka.dispatch.Futures.promise();
  private boolean isDBCompatible = true;

  private ExchangeRate exchangeRate = new ExchangeRate();

  @Override
  public void onCreate() {
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this);
    }
    dbHelper = new DBHelper(getApplicationContext());

    try {
      final File datadir = new File(getApplicationContext().getFilesDir(), DATADIR_NAME);
      Log.d(TAG, "Accessing Eclair Setup with datadir " + datadir.getAbsolutePath());

      EclairBitcoinjKit eclairBitcoinjKit = new EclairBitcoinjKit("test", datadir, this);
      Future<Wallet> fWallet = eclairBitcoinjKit.getFutureWallet();
      Future<PeerGroup> fPeerGroup = eclairBitcoinjKit.getFuturePeerGroup();
      EclairWallet eclairWallet = new BitcoinjWallet(fWallet, system.dispatcher());
      eclairBitcoinjKit.startAsync();

      Class.forName("org.sqlite.JDBC");
      Setup setup = new Setup(datadir, Option.apply(eclairWallet), ConfigFactory.empty(), system);
      ActorRef guiUpdater = system.actorOf(Props.create(EclairEventService.class, dbHelper));
      setup.system().eventStream().subscribe(guiUpdater, ChannelEvent.class);
      setup.system().eventStream().subscribe(guiUpdater, PaymentEvent.class);
      setup.system().eventStream().subscribe(guiUpdater, NetworkEvent.class);
      Future<Kit> fKit = setup.bootstrap();
      pAtCurrentHeight.completeWith(setup.bitcoin().left().get().atCurrentHeight());

      wallet = Await.result(fWallet, Duration.create(20, "seconds"));
      peerGroup = Await.result(fPeerGroup, Duration.create(20, "seconds"));
      eclairKit = Await.result(fKit, Duration.create(20, "seconds"));
      try {
        DBCompatChecker.checkDBCompatibility(setup.nodeParams());
      } catch (Exception e) {
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
    Coin balance = getWalletBalanceSat();
    EventBus.getDefault().postSticky(new WalletBalanceUpdateEvent(new Satoshi(balance.getValue())));
  }

  public Coin getWalletBalanceSat() {
    return wallet.getBalance(Wallet.BalanceType.AVAILABLE_SPENDABLE);
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
    return wallet.getNetworkParameters() == address.getParameters();
  }

  public void sendBitcoinPayment(final SendRequest sendRequest) throws InsufficientMoneyException {
    wallet.sendCoins(sendRequest);
  }

  public String getWalletNetworkProtocol() {
    return wallet.getNetworkParameters().getPaymentProtocolId();
  }

  public boolean isProduction() {
    return NetworkParameters.ID_MAINNET.equals(wallet.getNetworkParameters().getId());
  }

  public void broadcastTx(final String payload) {
    final Transaction tx = new Transaction(wallet.getParams(), Hex.decode(payload));
    Futures.addCallback(peerGroup.broadcastTransaction(tx).future(), new FutureCallback<Transaction>() {
      @Override
      public void onSuccess(@Nullable Transaction result) {
        Log.i(TAG, "Successful broadcast of " + tx.getHashAsString());
      }

      @Override
      public void onFailure(Throwable t) {
        Log.e(TAG, "Failed broadcast of " + tx.getHashAsString(), t);
      }
    });
  }

  public String nodePublicKey() {
    return eclairKit.nodeParams().privateKey().publicKey().toBin().toString();
  }

  public String getWalletPublicAddress() {
    return wallet.currentAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS).toBase58();
  }

  public String getRecoveryPhrase() {
    return Joiner.on(" ").join(wallet.getKeyChainSeed().getMnemonicCode());
  }

  public boolean checkWordRecoveryPhrase(int position, String word) {
    return wallet.getKeyChainSeed().getMnemonicCode().get(position).equals(word);
  }

  public long estimateSlowFees() {
    return 100;
  }

  public long estimateMediumFees() {
    return 215;
  }

  public long estimateFastFees() {
    return 350;
  }

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

  public void updateExchangeRate(Double eurRate, Double usdRate) {
    this.exchangeRate.eurRate = eurRate;
    this.exchangeRate.usdRate = usdRate;
  }

  public Double getEurRate() {
    return this.exchangeRate.eurRate;
  }

  public Double getUsdRate() {
    return this.exchangeRate.usdRate;
  }

  static class ExchangeRate {
    public Double eurRate = 0.0;
    public Double usdRate = 0.0;
  }
}


