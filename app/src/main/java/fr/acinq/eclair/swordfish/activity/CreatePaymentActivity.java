package fr.acinq.eclair.swordfish.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.wallet.SendRequest;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.util.AsyncExecutor;

import java.util.Date;
import java.util.List;

import akka.dispatch.OnComplete;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.crypto.Sphinx;
import fr.acinq.eclair.payment.PaymentFailed;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.payment.PaymentSucceeded;
import fr.acinq.eclair.swordfish.EclairHelper;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.customviews.CoinAmountView;
import fr.acinq.eclair.swordfish.events.LNPaymentFailedEvent;
import fr.acinq.eclair.swordfish.model.Payment;
import fr.acinq.eclair.swordfish.model.PaymentTypes;
import fr.acinq.eclair.swordfish.tasks.BitcoinInvoiceReaderTask;
import fr.acinq.eclair.swordfish.tasks.LNInvoiceReaderTask;
import fr.acinq.eclair.swordfish.utils.CoinUtils;
import scala.util.Either;

public class CreatePaymentActivity extends Activity
  implements LNInvoiceReaderTask.AsyncInvoiceReaderTaskResponse, BitcoinInvoiceReaderTask.AsyncInvoiceReaderTaskResponse {

  public static final String EXTRA_INVOICE = "fr.acinq.eclair.swordfish.EXTRA_INVOICE";
  private static final String TAG = "CreatePayment";

  private boolean isProcessingPayment = false;
  private PaymentRequest mLNInvoice = null;
  private String mLNInvoiceAsString = null;
  private BitcoinURI mBitcoinInvoice = null;

  private View mLoadingView;
  private TextView mLoadingTextView;
  private View mFormView;
  private CoinAmountView mAmountView;
  private TextView mDescriptionView;

  @Override
  public void processBitcoinInvoiceFinish(BitcoinURI output) {
    if (output == null) {
      mLoadingTextView.setTextColor(getResources().getColor(R.color.red));
      mLoadingTextView.setText("Could not read invoice!");
      mLoadingView.setClickable(true);
      mLoadingView.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          finish();
        }
      });
    } else {
      mBitcoinInvoice = output;
      mAmountView.setAmountMsat(package$.MODULE$.satoshi2millisatoshi(new Satoshi(output.getAmount().getValue())));
      mDescriptionView.setText(output.getAddress().toBase58());
      mLoadingView.setVisibility(View.GONE);
      mFormView.setVisibility(View.VISIBLE);
    }
  }

  @Override
  public void processLNInvoiceFinish(PaymentRequest output) {
    if (output == null) {
      new BitcoinInvoiceReaderTask(this, mLNInvoiceAsString).execute();
    } else {
      mLNInvoice = output;
      mAmountView.setAmountMsat(CoinUtils.getAmountFromInvoice(output));
      Either<String, BinaryData> desc = output.description();
      mDescriptionView.setText(desc.isLeft() ? desc.left().get() : desc.right().get().toString());
      mLoadingView.setVisibility(View.GONE);
      mFormView.setVisibility(View.VISIBLE);
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_create_payment);

    mFormView = findViewById(R.id.payment_form);
    mLoadingView = findViewById(R.id.payment_loading);
    mLoadingTextView = (TextView) findViewById(R.id.payment_loading_text);
    mAmountView = (CoinAmountView) findViewById(R.id.payment_value_amount);
    mDescriptionView = (TextView) findViewById(R.id.payment_description);

    Intent intent = getIntent();
    mLNInvoiceAsString = intent.getStringExtra(EXTRA_INVOICE);
    new LNInvoiceReaderTask(this, mLNInvoiceAsString).execute();
  }

  public void cancelPayment(View view) {
    finish();
  }

  public void sendPayment(final View view) {
    isProcessingPayment = true;
    toggleButtons();
    if (mLNInvoice != null) {
      sendLNPayment();
    } else if (mBitcoinInvoice != null) {
      sendBitcoinPayment();
    }
    finish();
  }

  private void sendLNPayment() {
    Log.i(TAG, "Sending LN payment for invoice " + mLNInvoiceAsString);
    final PaymentRequest pr = mLNInvoice;
    final String prAsString = mLNInvoiceAsString;
    AsyncExecutor.create().execute(
      new AsyncExecutor.RunnableEx() {
        @Override
        public void run() throws Exception {
          // 0 - Check if payment already exists
          Payment paymentForH = Payment.getPayment(pr.paymentHash().toString(), PaymentTypes.LN);

          // 1 - save payment attempt in DB
          final Payment p = paymentForH == null ? new Payment(PaymentTypes.LN) : paymentForH;
          if (paymentForH == null) {
            p.amountRequested = CoinUtils.getLongAmountFromInvoice(pr);
            p.paymentReference = pr.paymentHash().toString();
            p.paymentRequest = prAsString;
            p.status = "PENDING";
            p.description = pr.description().isLeft() ? pr.description().left().get() : pr.description().right().get().toString();
            p.created = new Date();
            p.updated = new Date();
            p.save();
          } else if ("PAID".equals(paymentForH.status)) {
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
  }

  private void sendBitcoinPayment() {
    Log.i(TAG, "Sending Bitcoin payment for invoice " + mBitcoinInvoice.toString());
    try {
      EclairHelper.sendBitcoinPayment(this.getBaseContext(), SendRequest.to(mBitcoinInvoice.getAddress(), mBitcoinInvoice.getAmount()));
      Toast.makeText(this, "Sent transaction", Toast.LENGTH_SHORT).show();
    } catch (Throwable t) {
      Log.e(TAG, "Could not send Bitcoin payment", t);
    }
  }

  private void toggleButtons() {
    if (isProcessingPayment) {
      this.findViewById(R.id.payment_layout_buttons).setVisibility(View.GONE);
      this.findViewById(R.id.payment_feedback).setVisibility(View.VISIBLE);
    }
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    toggleButtons();
  }

}
