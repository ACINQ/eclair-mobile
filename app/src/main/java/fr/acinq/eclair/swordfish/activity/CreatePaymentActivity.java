package fr.acinq.eclair.swordfish.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
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
import fr.acinq.eclair.swordfish.App;
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
  private TextView mDescriptionLabelView;
  private TextView mDescriptionView;
  private TextView mAmountInvalidView;
  private View mAmountReadonlyView;
  private View mAmountEditableView;
  private EditText mAmountEditableValue;
  private boolean isAmountReadonly = true;
  private EclairHelper eclairHelper;

  @Override
  public void processBitcoinInvoiceFinish(BitcoinURI output) {
    if (output == null || output.getAddress() == null) {
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
      isAmountReadonly = output.getAmount() != null;
      setAmountViewVisibility();
      mDescriptionLabelView.setText(R.string.destination_address);
      if (isAmountReadonly) {
        mAmountView.setAmountMsat(package$.MODULE$.satoshi2millisatoshi(new Satoshi(output.getAmount().getValue())));
      }
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
      mDescriptionLabelView.setText(R.string.description);
      isAmountReadonly = output.amount().isDefined();
      setAmountViewVisibility();
      if (isAmountReadonly) {
        mAmountView.setAmountMsat(CoinUtils.getAmountFromInvoice(output));
      }
      Either<String, BinaryData> desc = output.description();
      mDescriptionView.setText(desc.isLeft() ? desc.left().get() : desc.right().get().toString());
      mLoadingView.setVisibility(View.GONE);
      mFormView.setVisibility(View.VISIBLE);
    }
  }

  private void setAmountViewVisibility() {
    if (isAmountReadonly) {
      mAmountReadonlyView.setVisibility(View.VISIBLE);
      mAmountEditableView.setVisibility(View.GONE);
      mAmountEditableValue.clearFocus();
    } else {
      mAmountReadonlyView.setVisibility(View.GONE);
      mAmountEditableView.setVisibility(View.VISIBLE);
      mAmountEditableValue.requestFocus();
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    eclairHelper = ((App) getApplication()).getEclairInstance();
    setContentView(R.layout.activity_create_payment);

    mFormView = findViewById(R.id.payment_form);
    mLoadingView = findViewById(R.id.payment_loading);
    mLoadingTextView = (TextView) findViewById(R.id.payment_loading_text);
    mAmountView = (CoinAmountView) findViewById(R.id.payment_value_amount);
    mDescriptionLabelView = (TextView) findViewById(R.id.paymentitem_description_label);
    mDescriptionView = (TextView) findViewById(R.id.payment_description);
    mAmountReadonlyView = findViewById(R.id.payment_amount_readonly);
    mAmountEditableView = findViewById(R.id.payment_amount_editable);
    mAmountEditableValue = (EditText) findViewById(R.id.payment_amount_editable_value);
    mAmountInvalidView = (TextView) findViewById(R.id.payment_amount_invalid);

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
    try {
      if (mLNInvoice != null) {
          final long amountMsat = isAmountReadonly
            ? CoinUtils.getLongAmountFromInvoice(mLNInvoice)
            : package$.MODULE$.satoshi2millisatoshi(new Satoshi(Coin.parseCoin(mAmountEditableValue.getText().toString()).getValue())).amount();
          sendLNPayment(amountMsat);
      } else if (mBitcoinInvoice != null) {
        final Coin amount = isAmountReadonly ? mBitcoinInvoice.getAmount() : Coin.parseCoin(mAmountEditableValue.getText().toString());
        sendBitcoinPayment(amount);
      }
      finish();
    } catch (Exception e) {
      isProcessingPayment = false;
      toggleButtons();
      mAmountInvalidView.setVisibility(View.VISIBLE);
      (new Handler()).postDelayed(new Runnable() {
        public void run() {
          mAmountInvalidView.setVisibility(View.GONE);
        }
      }, 1500);
      Log.e(TAG, "Could not send payment", e);
    }
  }

  private void sendLNPayment(final long amountMsat) {
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
            p.amountRequestedMsat = CoinUtils.getLongAmountFromInvoice(pr);
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
              final Payment paymentInDB = Payment.getPayment(pr.paymentHash().toString(), PaymentTypes.LN);
              if (paymentInDB != null) {
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
          eclairHelper.sendPayment(45, onComplete, amountMsat, pr.paymentHash(), pr.nodeId());
        }
      }
    );
  }

  private void sendBitcoinPayment(final Coin amount) {
    Log.i(TAG, "Sending Bitcoin payment for invoice " + mBitcoinInvoice.toString());
    try {
      eclairHelper.sendBitcoinPayment(SendRequest.to(mBitcoinInvoice.getAddress(), amount));
      Toast.makeText(this, "Sent transaction", Toast.LENGTH_SHORT).show();
    } catch (InsufficientMoneyException e) {
      Toast.makeText(this, "Insufficient balance", Toast.LENGTH_LONG).show();
    } catch (Throwable t) {
      Toast.makeText(this, "Could not send transaction", Toast.LENGTH_LONG).show();
      Log.e(TAG, "Could not send Bitcoin payment", t);
    }
  }

  private void toggleButtons() {
    if (isProcessingPayment) {
      this.findViewById(R.id.payment_layout_buttons).setVisibility(View.GONE);
      this.findViewById(R.id.payment_feedback).setVisibility(View.VISIBLE);
    } else {
      this.findViewById(R.id.payment_layout_buttons).setVisibility(View.VISIBLE);
      this.findViewById(R.id.payment_feedback).setVisibility(View.GONE);
    }
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    toggleButtons();
  }

}
