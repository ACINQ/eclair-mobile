package fr.acinq.eclair.swordfish;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.util.Date;
import java.util.List;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.util.Timeout;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.payment.PaymentFailed;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.payment.PaymentResult;
import fr.acinq.eclair.payment.PaymentSucceeded;
import fr.acinq.eclair.payment.SendPayment;
import fr.acinq.eclair.swordfish.model.Payment;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

public class SendPaymentTask extends AsyncTask<String, Integer, SendPaymentTask.PaymentFeedback> {

  public class PaymentFeedback {
    private final boolean hasSucceeded;
    private final String message;
    private final PaymentRequest paymentRequest;

    public PaymentFeedback(boolean hasSucceeded, String message, PaymentRequest paymentRequest) {
      this.hasSucceeded = hasSucceeded;
      this.message = message;
      this.paymentRequest = paymentRequest;
    }
    public String getMessage() {
      return this.message;
    }
    public boolean hasSucceeded() {
      return this.hasSucceeded;
    }
  }

  public interface AsyncSendPaymentResponse {
    void processFinish(PaymentFeedback output);
  }

  private AsyncSendPaymentResponse delegate;
  private Context context;
  private PaymentRequest paymentRequest;

  public SendPaymentTask(AsyncSendPaymentResponse delegate, Context context, PaymentRequest paymentRequest) {
    this.delegate = delegate;
    this.context = context;
    this.paymentRequest = paymentRequest;
  }

  @Override
  protected PaymentFeedback doInBackground(String... params) {
    Timeout timeout = new Timeout(Duration.create(10, "seconds"));
    ActorRef paymentInitiator = EclairHelper.getInstance(context).getSetup().paymentInitiator();
    BinaryData paymentHash = BinaryData.apply(paymentRequest.paymentHash().toString());
    BinaryData nodeId = BinaryData.apply(paymentRequest.nodeId().toString());
    Crypto.Point pointNodeId = new Crypto.Point(Crypto.curve().getCurve().decodePoint(package$.MODULE$.binaryData2array(nodeId)));
    Crypto.PublicKey publicKey = new Crypto.PublicKey(pointNodeId, true);

    Future<Object> paymentFuture = Patterns.ask(paymentInitiator, new SendPayment(paymentRequest.amount().amount(), paymentHash, publicKey, 5), timeout);
    boolean hasSucceeded = false;
    String message = "";
    try {
      PaymentResult paymentResult = (PaymentResult) Await.result(paymentFuture, timeout.duration());
      if (paymentResult instanceof PaymentSucceeded) {
        Payment p = new Payment(paymentRequest.paymentHash().toString(), PaymentRequest.write(paymentRequest),
          "Placeholder description", new Date(), new Date());
        p.save();
        hasSucceeded = true;
      } else if (paymentResult instanceof PaymentFailed) {
        PaymentFailed pf = ((PaymentFailed) paymentResult);
        if (pf.error().isDefined()) {
          message = pf.error().get().failureMessage().toString();
        } else {
          message = "Unknown Error";
        }
      }
    } catch (Exception e) {
      message = e.getMessage();
      Log.e("Payment Task", "Payment has failed for hash " + paymentRequest.paymentHash().toString(), e);
    }
    return new PaymentFeedback(hasSucceeded, message, paymentRequest);
  }

  protected void onPostExecute(PaymentFeedback result) {
    delegate.processFinish(result);
  }
}
