package fr.acinq.eclair.swordfish;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

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
import akka.actor.Props;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import akka.util.Timeout;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.Setup;
import fr.acinq.eclair.blockchain.spv.BitcoinjKit2;
import fr.acinq.eclair.blockchain.wallet.BitcoinjWallet;
import fr.acinq.eclair.blockchain.wallet.EclairWallet;
import fr.acinq.eclair.channel.ChannelEvent;
import fr.acinq.eclair.io.Switchboard;
import fr.acinq.eclair.payment.PaymentEvent;
import fr.acinq.eclair.payment.SendPayment;
import fr.acinq.eclair.router.NetworkEvent;
import fr.acinq.eclair.swordfish.activity.LauncherActivity;
import fr.acinq.eclair.swordfish.events.BitcoinPaymentEvent;
import fr.acinq.eclair.swordfish.events.WalletBalanceUpdateEvent;
import fr.acinq.eclair.swordfish.model.Payment;
import fr.acinq.eclair.swordfish.model.PaymentTypes;
import scala.Option;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

public class EclairHelper {
  private static final String TAG = "EclairHelper";
  private static EclairHelper mInstance = null;
  private ActorRef guiUpdater;
  private BitcoinjKit2 kit2;
  private Setup setup;

  private EclairHelper() {
  }

  private EclairHelper(Context context) {
    try {
      Log.i(TAG, "Accessing Eclair Setup with datadir in " + context.getFilesDir().getAbsolutePath());
      File datadir = new File(context.getFilesDir(), "eclair-wallet-data");
      System.setProperty("eclair.node-alias", "sw-ripley");

      kit2 = new BitcoinjKit2("test", datadir) {
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
              if (tx.getFee() != null) paymentSent.feesPaidMsat = package$.MODULE$.satoshi2millisatoshi(new Satoshi(tx.getFee().getValue())).amount();
              paymentSent.updated = tx.getUpdateTime();
              paymentSent.save();
              EventBus.getDefault().post(new BitcoinPaymentEvent(paymentSent));
            }
          });
          super.onSetupCompleted();
        }
      };
      kit2.startAsync();
      Await.result(kit2.initialized(), Duration.create(20, "seconds"));
      setup = new Setup(datadir, "system", Option.apply((EclairWallet) new BitcoinjWallet(kit2.wallet())));
      guiUpdater = this.setup.system().actorOf(Props.create(EclairEventService.class));
      setup.system().eventStream().subscribe(guiUpdater, ChannelEvent.class);
      setup.system().eventStream().subscribe(guiUpdater, PaymentEvent.class);
      setup.system().eventStream().subscribe(guiUpdater, NetworkEvent.class);
      setup.boostrap();

    } catch (Exception e) {
      Log.e(TAG, "Failed to start eclair", e);
      if (setup != null) {
        setup.system().shutdown();
        setup.system().awaitTermination();
        setup = null;
        mInstance = null;
      }
    }
  }

  public static void startup(Context context) {
    if (!isEclairReady()) {
      Class clazz = EclairHelper.class;
      synchronized (clazz) {
        mInstance = new EclairHelper(context);
      }
    }
  }

  private static boolean checkEclairReady(Context context) {
    if (!isEclairReady()) {
      // eclair is not correctly loaded, clear task and redirection to Launcher
      Intent intent = new Intent(context, LauncherActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      intent.putExtra(LauncherActivity.EXTRA_AUTOSTART, false);
      context.startActivity(intent);
      return false;
    }
    return true;
  }

  public static boolean isEclairReady() {
    return mInstance != null && mInstance.setup != null;
  }

  public static void getWalletBalance(Context context) {
    if (checkEclairReady(context)) {
      Coin coin = mInstance.kit2.wallet().getBalance();
      EventBus.getDefault().postSticky(new WalletBalanceUpdateEvent(new Satoshi(coin.getValue())));
    }
  }

  public static void sendPayment(Context context, int timeout, OnComplete<Object> onComplete, long amountMsat, BinaryData paymentHash, Crypto.PublicKey targetNodeId) {
    if (checkEclairReady(context)) {
      Future<Object> paymentFuture = Patterns.ask(
        mInstance.setup.paymentInitiator(),
        new SendPayment(amountMsat, paymentHash, targetNodeId, 5),
        new Timeout(Duration.create(timeout, "seconds")));
      paymentFuture.onComplete(onComplete, mInstance.setup.system().dispatcher());
    }
  }

  public static void openChannel(Context context, int timeout, OnComplete<Object> onComplete,
                                 Crypto.PublicKey publicKey, InetSocketAddress address, Switchboard.NewChannel channel) {
    if (checkEclairReady(context) && publicKey != null && address != null && channel != null) {
      Future<Object> openChannelFuture = Patterns.ask(
        mInstance.setup.switchboard(),
        new Switchboard.NewConnection(publicKey, address, Option.apply(channel)),
        new Timeout(Duration.create(timeout, "seconds")));
      openChannelFuture.onComplete(onComplete, mInstance.setup.system().dispatcher());
    }
  }

  public static String nodeAlias(Context context) {
    if (checkEclairReady(context)) {
      return mInstance.setup.nodeParams().alias();
    }
    return "Unknown";
  }

  public static Wallet.SendResult sendBitcoinPayment(Context context, SendRequest sendRequest) throws InsufficientMoneyException, EclairStateException {
    if (checkEclairReady(context)) {
      return mInstance.kit2.wallet().sendCoins(sendRequest);
    }
    throw new EclairStateException();
  }

  public static String nodePublicKey(Context context) {
    if (checkEclairReady(context)) {
      return mInstance.setup.nodeParams().privateKey().publicKey().toBin().toString();
    }
    return "Unknown";
  }

  public static String getWalletPublicAddress(Context context) {
    if (checkEclairReady(context)) {
      return mInstance.kit2.wallet().freshSegwitAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS).toBase58();
    }
    return "Unknown";
  }
}
