package fr.acinq.eclair.wallet;

import android.util.Log;

import com.orm.SugarApp;
import com.typesafe.config.ConfigFactory;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.listeners.TransactionConfidenceEventListener;
import org.bitcoinj.wallet.KeyChain;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.net.InetSocketAddress;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.Futures;
import akka.dispatch.Mapper;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import akka.util.Timeout;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.Kit;
import fr.acinq.eclair.Setup;
import fr.acinq.eclair.blockchain.spv.BitcoinjKit2;
import fr.acinq.eclair.blockchain.wallet.BitcoinjWallet;
import fr.acinq.eclair.blockchain.wallet.EclairWallet;
import fr.acinq.eclair.channel.ChannelEvent;
import fr.acinq.eclair.io.Switchboard;
import fr.acinq.eclair.payment.PaymentEvent;
import fr.acinq.eclair.payment.SendPayment;
import fr.acinq.eclair.router.NetworkEvent;
import fr.acinq.eclair.wallet.events.BitcoinPaymentEvent;
import fr.acinq.eclair.wallet.events.WalletBalanceUpdateEvent;
import fr.acinq.eclair.wallet.model.Payment;
import fr.acinq.eclair.wallet.model.PaymentTypes;
import scala.Option;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.Promise;
import scala.concurrent.duration.Duration;

public class App extends SugarApp {

  public final static String TAG = "App";
  public final static String DATADIR_NAME = "eclair-wallet-data";
  private final ActorSystem system = ActorSystem.apply("system");

  private Wallet wallet;
  private Kit eclairKit;

  @Override
  public void onCreate() {
    try {
      final File datadir = new File(getApplicationContext().getFilesDir(), DATADIR_NAME);
      Log.d(TAG, "Accessing Eclair Setup with datadir " + datadir.getAbsolutePath());

      EclairBitcoinjKit eclairBitcoinjKit = new EclairBitcoinjKit("test", datadir);
      Future<Wallet> fWallet = eclairBitcoinjKit.getFutureWallet();
      Future<PeerGroup> fPeerGroup = eclairBitcoinjKit.getFuturePeerGroup();
      EclairWallet eclairWallet = new BitcoinjWallet(fWallet, fPeerGroup, system.dispatcher());
      eclairBitcoinjKit.startAsync();

      Setup setup = new Setup(datadir, Option.apply(eclairWallet), ConfigFactory.empty(), system);
      ActorRef guiUpdater = system.actorOf(Props.create(EclairEventService.class));
      setup.system().eventStream().subscribe(guiUpdater, ChannelEvent.class);
      setup.system().eventStream().subscribe(guiUpdater, PaymentEvent.class);
      setup.system().eventStream().subscribe(guiUpdater, NetworkEvent.class);
      Future<Kit> fKit = setup.bootstrap();

      wallet = Await.result(fWallet, Duration.create(20, "seconds"));
      eclairKit = Await.result(fKit, Duration.create(20, "seconds"));

    } catch (Exception e) {
      Log.e(TAG, "Failed to start eclair", e);
      throw new EclairStartException();
    }
    super.onCreate();
  }

  public void publishWalletBalance() {
    Coin balance = wallet.getBalance();
    EventBus.getDefault().postSticky(new WalletBalanceUpdateEvent(new Satoshi(balance.getValue())));
  }

  public Coin getWalletBalanceSat() {
    return wallet.getBalance();
  }

  public void sendPayment(int timeout, OnComplete<Object> onComplete, long amountMsat, BinaryData paymentHash, Crypto.PublicKey targetNodeId) {
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

  public void sendBitcoinPayment(final SendRequest sendRequest) throws InsufficientMoneyException {
    wallet.sendCoins(sendRequest);
  }

  public String nodePublicKey() {
    return eclairKit.nodeParams().privateKey().publicKey().toBin().toString();
  }

  public String getWalletPublicAddress() {
    return wallet.currentAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS).toBase58();
  }
}


