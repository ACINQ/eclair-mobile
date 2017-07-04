package fr.acinq.eclair.swordfish;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.net.InetSocketAddress;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import akka.util.Timeout;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.eclair.Setup;
import fr.acinq.eclair.channel.ChannelEvent;
import fr.acinq.eclair.io.Switchboard;
import fr.acinq.eclair.payment.PaymentEvent;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.payment.SendPayment;
import fr.acinq.eclair.router.NetworkEvent;
import fr.acinq.eclair.swordfish.activity.LauncherActivity;
import fr.acinq.eclair.swordfish.events.WalletBalanceUpdateEvent;
import fr.acinq.eclair.swordfish.utils.CoinUtils;
import scala.Option;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

public class EclairHelper {
  private static final String TAG = "EclairHelper";
  private static EclairHelper mInstance = null;
  private ActorRef guiUpdater;
  private Setup setup;

  private EclairHelper() {
  }

  private EclairHelper(Context context) {
    try {
      Log.i("Eclair Helper", "Accessing Eclair Setup with datadir in " + context.getFilesDir().getAbsolutePath());
      File data = new File(context.getFilesDir(), "eclair-wallet-data");
      System.setProperty("eclair.node-alias", "sw-ripley");

      setup = new Setup(data, "system");
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

  public static void pullWalletBalance(Context context) {
    if (checkEclairReady(context)) {
      ExecutionContext ec = mInstance.setup.system().dispatcher();
      mInstance.setup.blockExplorer().getBalance(ec).onComplete(new OnComplete<Satoshi>() {
        @Override
        public void onComplete(Throwable t, Satoshi balance) {
          if (t == null && balance != null) {
            Log.d(TAG, "Wallet balance is (satoshis) " + balance.amount());
            EventBus.getDefault().postSticky(new WalletBalanceUpdateEvent(balance));
          } else {
            Log.d(TAG, "Could not fetch wallet balance", t);
          }
        }
      }, ec);
    }
  }

  public static void sendPayment(Context context, int timeout, OnComplete<Object> onComplete, PaymentRequest paymentRequest) {
    if (checkEclairReady(context) && paymentRequest != null) {
      Future<Object> paymentFuture = Patterns.ask(
        mInstance.setup.paymentInitiator(),
        new SendPayment(CoinUtils.getLongAmountFromInvoice(paymentRequest), paymentRequest.paymentHash(), paymentRequest.nodeId(), 5),
        new Timeout(Duration.create(timeout, "seconds")));
      paymentFuture.onComplete(onComplete, mInstance.setup.system().dispatcher());
    }
  }

  public static void openChannel(Context context, int timeout, OnComplete<Object> onComplete, Crypto.PublicKey publicKey, InetSocketAddress address, Switchboard.NewChannel channel) {
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

  public static String nodePublicKey(Context context) {
    if (checkEclairReady(context)) {
      return mInstance.setup.nodeParams().privateKey().publicKey().toBin().toString();
    }
    return "Unknown";
  }

  public static String getWalletPublicAddress(Context context) {
    if (checkEclairReady(context)) {
      return mInstance.setup.blockExplorer().addr();
    }
    return "Unknown";
  }
}
