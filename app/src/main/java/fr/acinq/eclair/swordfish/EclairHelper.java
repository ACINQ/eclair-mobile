package fr.acinq.eclair.swordfish;

import android.content.Context;
import android.util.Log;

import com.typesafe.config.ConfigFactory;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
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
import fr.acinq.eclair.swordfish.events.BitcoinPaymentEvent;
import fr.acinq.eclair.swordfish.events.WalletBalanceUpdateEvent;
import fr.acinq.eclair.swordfish.model.Payment;
import fr.acinq.eclair.swordfish.model.PaymentTypes;
import scala.Option;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

public class EclairHelper {
  public final static String DATADIR_NAME = "eclair-wallet-data";
  private static final String TAG = "EclairHelper";
  private ActorRef guiUpdater;
  private BitcoinjKit2 bitcoinjKit2;
  //private Setup setup;
  private Kit eclairKit;

  public EclairHelper(Context context) throws EclairStartException {
    try {
      final File datadir = new File(context.getFilesDir(), DATADIR_NAME);
      Log.d(TAG, "Accessing Eclair Setup with datadir " + datadir.getAbsolutePath());
      System.setProperty("eclair.node-alias", "ewa");

      bitcoinjKit2 = new BitcoinjKit2("test", datadir) {
        @Override
        public void onSetupCompleted() {
          wallet().addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
              EventBus.getDefault().postSticky(new WalletBalanceUpdateEvent(new Satoshi(newBalance.getValue())));
              final Payment paymentInDB = Payment.getPayment(tx.getHashAsString(), PaymentTypes.BTC_RECEIVED);
              final Payment paymentSent = paymentInDB == null ? new Payment(PaymentTypes.BTC_RECEIVED) : paymentInDB;
              final Coin amountReceived = newBalance.minus(prevBalance);
              paymentSent.paymentReference = tx.getHashAsString();
              paymentSent.amountPaidMsat = package$.MODULE$.satoshi2millisatoshi(new Satoshi(amountReceived.getValue())).amount();
              paymentSent.updated = tx.getUpdateTime();
              paymentSent.save();
              publishWalletBalance();
              EventBus.getDefault().post(new BitcoinPaymentEvent(paymentSent));
            }
          });
          wallet().addCoinsSentEventListener(new WalletCoinsSentEventListener() {
            @Override
            public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
              EventBus.getDefault().postSticky(new WalletBalanceUpdateEvent(new Satoshi(newBalance.getValue())));
              final Payment paymentInDB = Payment.getPayment(tx.getHashAsString(), PaymentTypes.BTC_SENT);
              final Payment paymentSent = paymentInDB == null ? new Payment(PaymentTypes.BTC_SENT) : paymentInDB;
              final Coin amountSent = newBalance.minus(prevBalance);
              paymentSent.paymentReference = tx.getHashAsString();
              paymentSent.amountPaidMsat = package$.MODULE$.satoshi2millisatoshi(new Satoshi(amountSent.getValue())).amount();
              if (tx.getFee() != null)
                paymentSent.feesPaidMsat = package$.MODULE$.satoshi2millisatoshi(new Satoshi(tx.getFee().getValue())).amount();
              paymentSent.updated = tx.getUpdateTime();
              paymentSent.save();
              publishWalletBalance();
              EventBus.getDefault().post(new BitcoinPaymentEvent(paymentSent));
            }
          });
          super.onSetupCompleted();
        }
      };
      bitcoinjKit2.startAsync();
      final ActorSystem system = ActorSystem.apply("system");
      Future<Wallet> fWallet = bitcoinjKit2.initialized().map(new Mapper<Object, Wallet>() {
        public Wallet apply(Object isInitialized) {
          return bitcoinjKit2.wallet();
        }
      }, system.dispatcher());
      EclairWallet eclairWallet = new BitcoinjWallet(fWallet, system.dispatcher());
      Setup setup = new Setup(datadir, Option.apply(eclairWallet), ConfigFactory.empty(), system);
      guiUpdater = system.actorOf(Props.create(EclairEventService.class));
      setup.system().eventStream().subscribe(guiUpdater, ChannelEvent.class);
      setup.system().eventStream().subscribe(guiUpdater, PaymentEvent.class);
      setup.system().eventStream().subscribe(guiUpdater, NetworkEvent.class);
      Future<Kit> fKit = setup.bootstrap();
      eclairKit = Await.result(fKit, Duration.create(20, "seconds"));
    } catch (Exception e) {
      Log.e(TAG, "Failed to start eclair", e);
      throw new EclairStartException();
    }
  }

  public void publishWalletBalance() {
    Coin coin = this.bitcoinjKit2.wallet().getBalance();
    EventBus.getDefault().postSticky(new WalletBalanceUpdateEvent(new Satoshi(coin.getValue())));
  }

  public void sendPayment(int timeout, OnComplete<Object> onComplete, long amountMsat, BinaryData paymentHash, Crypto.PublicKey targetNodeId) {
    Future<Object> paymentFuture = Patterns.ask(
      this.eclairKit.paymentInitiator(),
      new SendPayment(amountMsat, paymentHash, targetNodeId, 5),
      new Timeout(Duration.create(timeout, "seconds")));
    paymentFuture.onComplete(onComplete, this.eclairKit.system().dispatcher());
  }

  public void openChannel(int timeout, OnComplete<Object> onComplete,
                          Crypto.PublicKey publicKey, InetSocketAddress address, Switchboard.NewChannel channel) {
    if (publicKey != null && address != null && channel != null) {
      Future<Object> openChannelFuture = Patterns.ask(
        this.eclairKit.switchboard(),
        new Switchboard.NewConnection(publicKey, address, Option.apply(channel)),
        new Timeout(Duration.create(timeout, "seconds")));
      openChannelFuture.onComplete(onComplete, this.eclairKit.system().dispatcher());
    }
  }

  public String nodeAlias() {
    return this.eclairKit.nodeParams().alias();
  }

  public Wallet.SendResult sendBitcoinPayment(SendRequest sendRequest) throws InsufficientMoneyException {
    return this.bitcoinjKit2.wallet().sendCoins(sendRequest);
  }

  public String nodePublicKey() {
    return this.eclairKit.nodeParams().privateKey().publicKey().toBin().toString();
  }

  public String getWalletPublicAddress() {
    return this.bitcoinjKit2.wallet().freshSegwitAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS).toBase58();
  }
}
