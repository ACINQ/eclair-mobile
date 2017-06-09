package fr.acinq.eclair.swordfish.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.greenrobot.eventbus.util.AsyncExecutor;
import org.greenrobot.eventbus.util.ThrowableFailureEvent;

import java.io.File;
import java.util.Date;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.util.Timeout;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.payment.PaymentFailed;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.payment.PaymentResult;
import fr.acinq.eclair.payment.PaymentSucceeded;
import fr.acinq.eclair.payment.SendPayment;
import fr.acinq.eclair.swordfish.EclairHelper;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.SWPaymentEvent;
import fr.acinq.eclair.swordfish.SWPaymentException;
import fr.acinq.eclair.swordfish.customviews.CoinAmountView;
import fr.acinq.eclair.swordfish.model.Payment;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

public class CreatePaymentActivity extends Activity {

  private PaymentRequest currentPR = null;
  private boolean isProcessingPayment = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_create_payment);

    Intent intent = getIntent();
    String prString = intent.getStringExtra(HomeActivity.EXTRA_PAYMENTREQUEST);
    CoinAmountView v_amount = (CoinAmountView) findViewById(R.id.payment__value_amount);
    try {
      PaymentRequest extract = PaymentRequest.read(prString);
      v_amount.setAmountSat(package$.MODULE$.millisatoshi2satoshi(extract.amount()));
      currentPR = extract;
    } catch (Throwable t) {
      Toast.makeText(this, "Invalid Payment Request", Toast.LENGTH_SHORT).show();
      goToHome();
    }
  }

  public void cancelPayment(View view) {
    goToHome();
  }

  private void goToHome() {
    this.currentPR = null;
    Intent intent = new Intent(this, HomeActivity.class);
    startActivity(intent);
  }

  public void sendPayment(View view) {
    isProcessingPayment = true;
    final File datadir = getFilesDir();
    toggleButtons();
    AsyncExecutor.create().execute(
      new AsyncExecutor.RunnableEx() {
        @Override
        public void run() throws Exception {

          // 1 - save payment attempt in DB
          Payment p = new Payment(currentPR.paymentHash().toString(), PaymentRequest.write(currentPR), "Placeholder description", new Date(), new Date());
          p.save();

          // 2 - prepare payment future ask
          Timeout paymentTimeout = new Timeout(Duration.create(60, "seconds"));
          ActorRef paymentInitiator = EclairHelper.getInstance(datadir).getSetup().paymentInitiator();
          Crypto.Point pointNodeId = new Crypto.Point(Crypto.curve().getCurve().decodePoint(package$.MODULE$.binaryData2array(currentPR.nodeId())));
          Crypto.PublicKey publicKey = new Crypto.PublicKey(pointNodeId, true);
          Future<Object> paymentFuture = Patterns.ask(paymentInitiator, new SendPayment(currentPR.amount().amount(), currentPR.paymentHash(), publicKey, 5), paymentTimeout);

          // 3 - execute payment future and read result
          PaymentResult paymentResult = (PaymentResult) Await.result(paymentFuture, paymentTimeout.duration());
          if (paymentResult instanceof PaymentSucceeded) {
            EventBus.getDefault().post(new SWPaymentEvent(currentPR));
          } else if (paymentResult instanceof PaymentFailed) {
            throw new SWPaymentException(); // generic EventBus error event is sent
          }
        }
      }
    );
    delayedGoToHome(4000);
  }

  private void toggleButtons() {
    if (isProcessingPayment) {
      this.findViewById(R.id.payment__layout_buttons).setVisibility(View.GONE);
      this.findViewById(R.id.payment__layout_feedback).setVisibility(View.VISIBLE);
    }
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    toggleButtons();
  }

  @Override
  public void onStart() {
    EventBus.getDefault().register(this);
    super.onStart();
  }

  @Override
  public void onStop() {
    EventBus.getDefault().unregister(this);
    super.onStop();
  }

  @Override
  public void onPause() {
    EventBus.getDefault().unregister(this);
    super.onPause();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleFailureEvent(ThrowableFailureEvent event) {
    Toast.makeText(this, "Payment failed because: " + event.getThrowable().getMessage(), Toast.LENGTH_LONG).show();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onMessageEvent(SWPaymentEvent event) {
    TextView feedbackView = (TextView) this.findViewById(R.id.payment__value_feedback);
    feedbackView.setTextColor(getResources().getColor(R.color.colorPrimary));
    feedbackView.setText("Payment Sent!");
    delayedGoToHome(2000);
  }

  private void delayedGoToHome(long delay) {
    Handler handler = new Handler();
    handler.postDelayed(new Runnable() {
      @Override
      public void run() {
        goToHome();
      }
    }, delay);
  }
}
