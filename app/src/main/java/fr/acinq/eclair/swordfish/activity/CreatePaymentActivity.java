package fr.acinq.eclair.swordfish.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.util.AsyncExecutor;

import java.io.File;
import java.util.Date;
import java.util.List;

import akka.dispatch.OnComplete;
import fr.acinq.eclair.crypto.Sphinx;
import fr.acinq.eclair.payment.PaymentFailed;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.payment.PaymentSucceeded;
import fr.acinq.eclair.swordfish.EclairHelper;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.customviews.CoinAmountView;
import fr.acinq.eclair.swordfish.events.LNPaymentFailedEvent;
import fr.acinq.eclair.swordfish.model.Payment;
import fr.acinq.eclair.swordfish.utils.CoinUtils;

public class CreatePaymentActivity extends Activity {

  public static final String EXTRA_INVOICE = "fr.acinq.eclair.swordfish.EXTRA_INVOICE";
  private static final String TAG = "CreatePayment";
  private PaymentRequest currentPR = null;
  private String currentPrAsString = null;
  private boolean isProcessingPayment = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_create_payment);
    CoinAmountView v_amount = (CoinAmountView) findViewById(R.id.payment__value_amount);

    Intent intent = getIntent();
    currentPrAsString = intent.getStringExtra(EXTRA_INVOICE);
    try {
      PaymentRequest extract = PaymentRequest.read(currentPrAsString);
      v_amount.setAmountMsat(CoinUtils.getAmountFromInvoice(extract));
      currentPR = extract;
    } catch (Throwable t) {
      Toast.makeText(this, "Invalid Invoice", Toast.LENGTH_LONG).show();
      finish();
    }
  }

  public void cancelPayment(View view) {
    finish();
  }

  public void sendPayment(final View view) {
    isProcessingPayment = true;
    final File datadir = getFilesDir();
    final PaymentRequest pr = currentPR;
    final String prAsString = currentPrAsString;
    toggleButtons();
    Log.i("Create Payment", "Sending payment...");
    AsyncExecutor.create().execute(
      new AsyncExecutor.RunnableEx() {
        @Override
        public void run() throws Exception {
          // 0 - Check if payment already exists
          List<Payment> paymentListForH = Payment.findWithQuery(Payment.class, "SELECT * FROM Payment WHERE payment_hash = ? LIMIT 1",
            pr.paymentHash().toString());

          // 1 - save payment attempt in DB
          final Payment p = paymentListForH.isEmpty() ? new Payment() : paymentListForH.get(0);
          if (paymentListForH.isEmpty()) {
            p.amountRequested = CoinUtils.getLongAmountFromInvoice(pr);
            p.paymentHash = pr.paymentHash().toString();
            p.paymentRequest = prAsString;
            p.status = "PENDING";
            p.description = pr.description().isLeft() ? pr.description().left().get() : pr.description().right().get().toString();
            p.created = new Date();
            p.updated = new Date();
            p.save();
          } else if ("PAID".equals(paymentListForH.get(0).status)) {
            EventBus.getDefault().post(new LNPaymentFailedEvent(p, "Invoice already paid."));
            return;
          }

          // 2 - setup future callback
          OnComplete<Object> onComplete = new OnComplete<Object>() {
            @Override
            public void onComplete(Throwable t, Object o) {
              List<Payment> freshPaymentListForH = Payment.findWithQuery(Payment.class, "SELECT * FROM Payment WHERE payment_hash = ? LIMIT 1",
                pr.paymentHash().toString());
              if (!freshPaymentListForH.isEmpty()) {
                Payment paymentInDB = freshPaymentListForH.get(0);
                if (t != null && t instanceof akka.pattern.AskTimeoutException) {
                  // payment is taking too long, let's do nothing and keep waiting
                } else {
                  p.updated = new Date();
                  if (o instanceof PaymentSucceeded && t == null) {
                    // do nothing, will be handled by PaymentSent event...
                  } else {
                    String cause = "Unknown Error";
                    if (o instanceof PaymentFailed) {
                      Sphinx.ErrorPacket error = ((PaymentFailed) o).error().get();
                      cause = error != null && error.failureMessage() != null ? error.failureMessage().toString() : cause;
                    } else if (t != null) {
                      Log.d(TAG, "Error when sending payment", t);
                      cause = t.getMessage();
                    }
                    if (!"PAID".equals(paymentInDB.status)) {
                      // if the payment has not already been paid, lets update the status...
                      paymentInDB.status = "FAILED";
                      paymentInDB.lastErrorCause = cause;
                    }
                    EventBus.getDefault().post(new LNPaymentFailedEvent(p, cause));
                    paymentInDB.save();
                  }
                }
              }
            }
          };

          // 3 - execute payment future
          EclairHelper.sendPayment(getApplicationContext(), 45, onComplete, pr);
        }
      }
    );
    Intent intent = new Intent(this, HomeActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    startActivity(intent);
    finish();
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
