package fr.acinq.eclair.wallet;

import android.content.Context;
import android.util.Log;

import com.typesafe.config.ConfigFactory;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
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

public class EclairHelper {
  public final static String DATADIR_NAME = "eclair-wallet-data";
  private static final String TAG = "EclairHelper";
  private final ActorRef guiUpdater;
  final ActorSystem system = ActorSystem.apply("system");
  private final Promise<Wallet> bitcoinjWallet = Futures.promise();
  private final Kit eclairKit;

  public EclairHelper(Context context) throws EclairStartException {
    try {
      final File datadir = new File(context.getFilesDir(), DATADIR_NAME);
      Log.d(TAG, "Accessing Eclair Setup with datadir " + datadir.getAbsolutePath());
      System.setProperty("eclair.node-alias", "ewa");

      BitcoinjKit2 bitcoinjKit2 = new BitcoinjKit2("test", datadir) {
        @Override
        public void onSetupCompleted() {
          wallet().addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
              EventBus.getDefault().postSticky(new WalletBalanceUpdateEvent(new Satoshi(newBalance.getValue())));
              final Payment paymentInDB = Payment.getPayment(tx.getHashAsString(), PaymentTypes.BTC_RECEIVED);
              final Payment paymentReceived = paymentInDB == null ? new Payment(PaymentTypes.BTC_RECEIVED) : paymentInDB;
              final Coin amountReceived = newBalance.minus(prevBalance);
              paymentReceived.paymentReference = tx.getHashAsString();
              paymentReceived.amountPaidMsat = package$.MODULE$.satoshi2millisatoshi(new Satoshi(amountReceived.getValue())).amount();
              paymentReceived.updated = tx.getUpdateTime();
              paymentReceived.confidenceBlocks = tx.getConfidence().getDepthInBlocks();
              paymentReceived.confidenceType = tx.getConfidence().getConfidenceType().getValue();
              paymentReceived.save();
              publishWalletBalance();
              EventBus.getDefault().post(new BitcoinPaymentEvent(paymentReceived));
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
              paymentSent.confidenceBlocks = tx.getConfidence().getDepthInBlocks();
              paymentSent.confidenceType = tx.getConfidence().getConfidenceType().getValue();
              paymentSent.save();
              publishWalletBalance();
              EventBus.getDefault().post(new BitcoinPaymentEvent(paymentSent));
            }
          });
          wallet().addTransactionConfidenceEventListener(new TransactionConfidenceEventListener() {
            @Override
            public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
              final Payment paymentInDB = Payment.getPayment(tx.getHashAsString());
              if (paymentInDB != null) {
                paymentInDB.confidenceBlocks = tx.getConfidence().getDepthInBlocks();
                paymentInDB.confidenceType = tx.getConfidence().getConfidenceType().getValue();
                paymentInDB.save();
              }
            }
          });
          bitcoinjWallet.success(wallet());
          super.onSetupCompleted();
        }
      };
      bitcoinjKit2.startAsync();


      EclairWallet eclairWallet = new BitcoinjWallet(bitcoinjWallet.future(), system.dispatcher());
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
    this.bitcoinjWallet.future().map(new Mapper<Wallet, Long>() {
      public Long apply(Wallet wallet) {
        Coin balance = wallet.getBalance();
        EventBus.getDefault().postSticky(new WalletBalanceUpdateEvent(new Satoshi(balance.getValue())));
        return Long.valueOf(balance.getValue());
      }
    }, system.dispatcher());
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

  public void sendBitcoinPayment(final SendRequest sendRequest) throws InsufficientMoneyException {
    if (bitcoinjWallet.isCompleted()) {
      bitcoinjWallet.future().value().get().get().sendCoins(sendRequest);
    } else {
      throw new EclairStartException();
    }
  }

  public String nodePublicKey() {
    return this.eclairKit.nodeParams().privateKey().publicKey().toBin().toString();
  }

  public String getWalletPublicAddress() {
    return bitcoinjWallet.isCompleted()
      ? bitcoinjWallet.future().value().get().get().freshSegwitAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS).toBase58()
      : "";
  }
}
