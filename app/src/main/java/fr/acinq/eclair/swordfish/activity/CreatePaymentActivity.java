package fr.acinq.eclair.swordfish.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.util.AsyncExecutor;
import org.greenrobot.eventbus.util.ThrowableFailureEvent;

import java.io.File;
import java.util.Date;
import java.util.List;

import akka.actor.ActorRef;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import akka.util.Timeout;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.payment.PaymentFailed;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.payment.PaymentSucceeded;
import fr.acinq.eclair.payment.SendPayment;
import fr.acinq.eclair.swordfish.EclairHelper;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.events.SWPaymentEvent;
import fr.acinq.eclair.swordfish.customviews.CoinAmountView;
import fr.acinq.eclair.swordfish.model.Payment;
import scala.concurrent.ExecutionContext;
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
    final PaymentRequest pr = currentPR;
    toggleButtons();
    Log.i("Create Payment", "Sending payment...");
    AsyncExecutor.create().execute(
      new AsyncExecutor.RunnableEx() {
        @Override
        public void run() throws Exception {

          // 1 - save payment attempt in DB
          Payment p = new Payment(pr.paymentHash().toString(), PaymentRequest.write(pr), "Placeholder description", new Date(), new Date());
          p.save();

          // 2 - prepare payment future ask
          Timeout paymentTimeout = new Timeout(Duration.create(60, "seconds"));
          ActorRef paymentInitiator = EclairHelper.getInstance(datadir).getSetup().paymentInitiator();
          Crypto.Point pointNodeId = new Crypto.Point(Crypto.curve().getCurve().decodePoint(package$.MODULE$.binaryData2array(pr.nodeId())));
          Crypto.PublicKey publicKey = new Crypto.PublicKey(pointNodeId, true);
          ExecutionContext ec = EclairHelper.getInstance(datadir).getSetup().system().dispatcher();

          // 3 - execute payment future and handle result
          Future<Object> paymentFuture = Patterns.ask(paymentInitiator,
            new SendPayment(pr.amount().amount(), pr.paymentHash(), publicKey, 5),
            paymentTimeout);
          paymentFuture.onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(Throwable t, Object o) {
              List<Payment> paymentList = Payment.findWithQuery(Payment.class, "SELECT * FROM Payment WHERE payment_hash = ? LIMIT 1",
                pr.paymentHash().toString());
              if (paymentList.isEmpty()) {
                Log.w("Payment Complete", "Received an unknown event -> ignored");
              } else {
                Payment paymentInDB = paymentList.get(0);
                paymentInDB.updated = new Date();
                if (o instanceof PaymentSucceeded && t == null) {
                  paymentInDB.status = "PAID";
                  EventBus.getDefault().post(new SWPaymentEvent(pr));
                } else {
                  paymentInDB.status = "FAILED";
                  if (t != null) {
                    EventBus.getDefault().post(new ThrowableFailureEvent(new Exception(t)));
                  } else if (o instanceof PaymentFailed){
                    EventBus.getDefault().post(new ThrowableFailureEvent(new Exception( ((PaymentFailed) o).error().get().failureMessage().toString() )));
                  } else {
                    EventBus.getDefault().post(new ThrowableFailureEvent(new Exception("Internal Error" )));
                  }
                }
                paymentInDB.save();
              }
            }
          }, ec);
        }
      }
    );
    goToHome();
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

}
