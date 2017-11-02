package fr.acinq.eclair.wallet.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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

import akka.dispatch.OnComplete;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.MilliBtc;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.channel.ChannelException;
import fr.acinq.eclair.payment.LocalFailure;
import fr.acinq.eclair.payment.PaymentFailed;
import fr.acinq.eclair.payment.PaymentFailure;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.payment.PaymentSucceeded;
import fr.acinq.eclair.payment.RemoteFailure;
import fr.acinq.eclair.router.Hop;
import fr.acinq.eclair.wallet.EclairEventService;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.customviews.CoinAmountView;
import fr.acinq.eclair.wallet.events.LNPaymentFailedEvent;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDirection;
import fr.acinq.eclair.wallet.models.PaymentStatus;
import fr.acinq.eclair.wallet.models.PaymentType;
import fr.acinq.eclair.wallet.tasks.BitcoinInvoiceReaderTask;
import fr.acinq.eclair.wallet.tasks.LNInvoiceReaderTask;
import fr.acinq.eclair.wallet.utils.CoinUtils;
import fr.acinq.eclair.wire.FailureMessage;
import scala.collection.Seq;
import scala.collection.mutable.StringBuilder;
import scala.math.BigDecimal;
import scala.util.Either;

public class CreatePaymentActivity extends EclairModalActivity
  implements LNInvoiceReaderTask.AsyncInvoiceReaderTaskResponse, BitcoinInvoiceReaderTask.AsyncInvoiceReaderTaskResponse {

  public static final String EXTRA_INVOICE = "fr.acinq.eclair.wallet.EXTRA_INVOICE";
  private static final String TAG = "CreatePayment";
  private final static String html_error_tab = "&nbsp;&nbsp;&nbsp;&nbsp;";
  private final static String html_error_new_line = "<br />" + html_error_tab;
  private final static String html_error_new_line_bullet = html_error_new_line + "&middot;&nbsp;&nbsp;";
  private final static String html_error_new_line_bullet_inner = html_error_new_line + html_error_tab + "&middot;&nbsp;&nbsp;";
  private boolean isProcessingPayment = false;
  private PaymentRequest mLNInvoice = null;
  private BitcoinURI mBitcoinInvoice = null;
  private String mInvoice = null;
  private TextView mLoadingTextView;
  private View mFormView;
  private CoinAmountView mAmountReadonlyValue;
  private TextView mDescriptionView;
  private TextView mDescriptionLabelView;
  private View mPaymentErrorView;
  private TextView mPaymentErrorTextView;
  private View mAmountEditableView;
  private EditText mAmountEditableValue;
  private TextView mAmountFiatView;
  private TextView mPaymentTypeView;
  private boolean isAmountReadonly = true;
  private EditText mFeesValue;
  private Button mFeesButton;
  private View mButtonsView;

  @SuppressLint("SetTextI18n")
  @Override
  public void processLNInvoiceFinish(PaymentRequest output) {
    if (output == null) {
      // try reading invoice as a bitcoin uri
      new BitcoinInvoiceReaderTask(this, mInvoice).execute();
    } else {
      // check if the user has any channels
      if (EclairEventService.getChannelsMap().size() == 0) {
        mButtonsView.setVisibility(View.GONE);
        mPaymentErrorTextView.setText(Html.fromHtml(getString(R.string.payment_error_amount_ln_no_channels)));
        mPaymentErrorView.setVisibility(View.VISIBLE);
      }
      mLNInvoice = output;
      isAmountReadonly = mLNInvoice.amount().isDefined();
      setAmountViewVisibility();
      if (isAmountReadonly) {
        final MilliSatoshi amountMsat = CoinUtils.getAmountFromInvoice(mLNInvoice);
        mAmountReadonlyValue.setAmountMsat(amountMsat);
        setFiatAmount(amountMsat);
      }
      Either<String, BinaryData> desc = output.description();
      invoiceReadSuccessfully(R.string.payment_description, desc.isLeft() ? desc.left().get() : desc.right().get().toString(),
        R.string.payment_type_lightning, R.color.colorPrimaryLight);
    }
  }

  @Override
  public void processBitcoinInvoiceFinish(BitcoinURI output) {
    if (output == null || output.getAddress() == null) {
      couldNotReadInvoice(R.string.payment_failure_read_invoice);
    } else if (!app.checkAddress(output.getAddress())) {
      couldNotReadInvoice(R.string.payment_invalid_address);
    } else {
      mBitcoinInvoice = output;
      isAmountReadonly = mBitcoinInvoice.getAmount() != null;
      if (isAmountReadonly) {
        final MilliSatoshi amountMsat = package$.MODULE$.satoshi2millisatoshi(new Satoshi(mBitcoinInvoice.getAmount().getValue()));
        mAmountReadonlyValue.setAmountMsat(amountMsat);
        setFiatAmount(amountMsat);
      }
      setFeesDefault();
      invoiceReadSuccessfully(R.string.payment_destination_address, output.getAddress().toBase58(),
        R.string.payment_type_onchain, R.color.orange);
    }
  }

  private void setFiatAmount(final MilliSatoshi amountMsat) {
    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
    String fiatCurrency = sharedPref.getString("fiat_currency", "eur");
    Double rate = fiatCurrency.equals("eur") ? app.getEurRate() : app.getUsdRate();
    final Double amountEur = package$.MODULE$.millisatoshi2btc(amountMsat).amount().doubleValue() * rate;
    mAmountReadonlyValue.setAmountMsat(amountMsat);
    mAmountFiatView.setText(CoinUtils.getFiatFormat().format(amountEur) + " " + fiatCurrency.toUpperCase());
  }

  private void couldNotReadInvoice(int causeMessageId) {
    mLoadingTextView.setTextColor(ContextCompat.getColor(this, R.color.redFaded));
    mLoadingTextView.setText(causeMessageId);
    mLoadingTextView.setClickable(true);
    mLoadingTextView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
      }
    });
  }

  private void invoiceReadSuccessfully(int descriptionLabel, String description, int paymentTypeLabel, int paymentTypeColor) {
    setAmountViewVisibility();
    // description
    mDescriptionLabelView.setText(descriptionLabel);
    mDescriptionView.setText(description);
    // payment type label
    mPaymentTypeView.setText(paymentTypeLabel);
    mPaymentTypeView.setBackgroundColor(getResources().getColor(paymentTypeColor));
    mPaymentTypeView.setVisibility(View.VISIBLE);
    // change visibility
    mLoadingTextView.setVisibility(View.GONE);
    mFormView.setVisibility(View.VISIBLE);
  }

  private void setAmountViewVisibility() {
    if (isAmountReadonly) {
      mAmountReadonlyValue.setVisibility(View.VISIBLE);
      mAmountEditableView.setVisibility(View.GONE);
      mAmountEditableValue.clearFocus();
    } else {
      mAmountReadonlyValue.setVisibility(View.GONE);
      mAmountEditableView.setVisibility(View.VISIBLE);
      mAmountEditableValue.requestFocus();
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_create_payment);

    mFormView = findViewById(R.id.payment_form);
    mLoadingTextView = findViewById(R.id.payment_loading);
    mDescriptionView = findViewById(R.id.payment_description);
    mDescriptionLabelView = findViewById(R.id.payment_description_label);
    mAmountReadonlyValue = findViewById(R.id.payment_amount_readonly_value);
    mAmountEditableView = findViewById(R.id.payment_amount_editable);
    mAmountEditableValue = findViewById(R.id.payment_amount_editable_value);
    mAmountEditableValue.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (s.length() > 0) {
          try {
            BigDecimal amount = BigDecimal.exact(s.toString());
            setFiatAmount(package$.MODULE$.millibtc2millisatoshi(new MilliBtc(amount)));
          } catch (NumberFormatException e) {
            Log.d(TAG, "Could not read amount", e);
          }
        }
      }

      @Override
      public void afterTextChanged(Editable s) {
      }
    });
    mAmountFiatView = findViewById(R.id.payment_amount_fiat);
    mPaymentTypeView = findViewById(R.id.payment_type);
    mPaymentErrorView = findViewById(R.id.payment_error);
    mPaymentErrorTextView = findViewById(R.id.payment_error_text);
    mFeesButton = findViewById(R.id.payment_fees_rating);
    mFeesValue = findViewById(R.id.payment_fees_value);
    mFeesValue.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @SuppressLint("SetTextI18n")
      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (s.length() > 0) {
          try {
            Long feesSatPerByte = Long.parseLong(s.toString());
            if (feesSatPerByte <= app.estimateSlowFees()) {
              mFeesButton.setText(R.string.payment_fees_slow);
            } else if (feesSatPerByte <= app.estimateMediumFees()) {
              mFeesButton.setText(R.string.payment_fees_medium);
            } else {
              mFeesButton.setText(R.string.payment_fees_fast);
            }
          } catch (NumberFormatException e) {
            Log.e(TAG, "Could not read fees", e);
            setFeesDefault();
          }
        }
      }

      @Override
      public void afterTextChanged(Editable s) {
      }
    });
    mButtonsView = findViewById(R.id.payment_layout_buttons);

    Intent intent = getIntent();
    mInvoice = intent.getStringExtra(EXTRA_INVOICE);
    new LNInvoiceReaderTask(this, mInvoice).execute();
  }

  @SuppressLint("SetTextI18n")
  public void pickFees(View view) {
    try {
      Long feesSatPerByte = Long.parseLong(mFeesValue.getText().toString());
      if (feesSatPerByte <= app.estimateSlowFees()) {
        mFeesValue.setText(app.estimateMediumFees().toString());
      } else if (feesSatPerByte <= app.estimateMediumFees()) {
        mFeesValue.setText(app.estimateFastFees().toString());
      } else {
        mFeesValue.setText(app.estimateSlowFees().toString());
      }
    } catch (NumberFormatException e) {
      Log.e(TAG, "Could not read fees", e);
      setFeesDefault();
    }
  }

  @SuppressLint("SetTextI18n")
  private void setFeesDefault() {
    mFeesValue.setText(app.estimateFastFees().toString());
  }

  public void cancelPayment(View view) {
    finish();
  }

  public void sendPayment(final View view) {
    if (isProcessingPayment) return;

    isProcessingPayment = true;
    mPaymentErrorView.setVisibility(View.GONE);
    mButtonsView.setVisibility(View.GONE);
    try {
      if (mLNInvoice != null) {
        final long amountMsat = isAmountReadonly
          ? CoinUtils.getLongAmountFromInvoice(mLNInvoice)
          : package$.MODULE$.satoshi2millisatoshi(new Satoshi(Coin.parseCoin(mAmountEditableValue.getText().toString()).div(1000).getValue())).amount();
        if (EclairEventService.hasActiveChannelsWithBalance(amountMsat)) {
          sendLNPayment(amountMsat);
          finish();
        } else {
          if (EclairEventService.hasActiveChannels()) {
            // Refine the error message to guide the user: he does not have enough balance on any of the channels
            mPaymentErrorTextView.setText(Html.fromHtml(getString(R.string.payment_error_amount_ln_insufficient_funds)));
          } else {
            // The user simply does not have any active channels
            mPaymentErrorTextView.setText(Html.fromHtml(getString(R.string.payment_error_amount_ln_no_active_channels)));
          }
          mButtonsView.setVisibility(View.GONE);
          mPaymentErrorView.setVisibility(View.VISIBLE);
        }
      } else if (mBitcoinInvoice != null) {
        final Coin amount = isAmountReadonly ? mBitcoinInvoice.getAmount() : Coin.parseCoin(mAmountEditableValue.getText().toString()).div(1000);
        try {
          final Coin feesPerKb = Coin.valueOf(Long.parseLong(mFeesValue.getText().toString()));
          sendBitcoinPayment(amount, feesPerKb);
          finish();
        } catch (NumberFormatException e) {
          mPaymentErrorTextView.setText(R.string.payment_error_fees_onchain);
          handlePaymentError();
        }
      }
    } catch (NumberFormatException e) {
      mPaymentErrorTextView.setText(R.string.payment_error_amount);
      handlePaymentError();
    } catch (Exception e) {
      Log.e(TAG, "Could not send payment", e);
      mPaymentErrorTextView.setText(R.string.payment_error); // generic message
      handlePaymentError();
    }
  }

  private void handlePaymentError() {
    isProcessingPayment = false;
    mButtonsView.setVisibility(View.VISIBLE);
    mPaymentErrorView.setVisibility(View.VISIBLE);
  }

  private void sendLNPayment(final long amountMsat) {
    Log.d(TAG, "Sending LN payment for invoice " + mInvoice);
    final PaymentRequest pr = mLNInvoice;
    final String prAsString = mInvoice;
    AsyncExecutor.create().execute(
      new AsyncExecutor.RunnableEx() {
        @Override
        public void run() throws Exception {
          // 0 - Check if payment already exists
          Payment paymentForH = app.getDBHelper().getPayment(pr.paymentHash().toString(), PaymentType.BTC_LN);

          // 1 - save payment attempt in DB
          final Payment p = paymentForH == null ? new Payment() : paymentForH;
          if (paymentForH == null) {
            p.setType(PaymentType.BTC_LN);
            p.setDirection(PaymentDirection.SENT);
            p.setReference(pr.paymentHash().toString());
            p.setAmountRequestedMsat(CoinUtils.getLongAmountFromInvoice(pr));
            p.setPaymentRequest(prAsString);
            p.setStatus(PaymentStatus.PENDING);
            p.setDescription(pr.description().isLeft()
              ? pr.description().left().get()
              : pr.description().right().get().toString());
            p.setUpdated(new Date());
            app.getDBHelper().insertOrUpdatePayment(p);
          } else if (PaymentStatus.PAID.equals(paymentForH.getStatus())) {
            EventBus.getDefault().post(new LNPaymentFailedEvent(p, "This invoice has already been paid.", null));
            return;
          }

          // 2 - setup future callback
          OnComplete<Object> onComplete = new OnComplete<Object>() {
            @Override
            public void onComplete(Throwable t, Object o) {
              final Payment paymentInDB = app.getDBHelper().getPayment(pr.paymentHash().toString(), PaymentType.BTC_LN);
              if (paymentInDB != null) {
                if (t != null && t instanceof akka.pattern.AskTimeoutException) {
                  // payment is taking too long, let's do nothing and keep waiting
                } else {
                  p.setUpdated(new Date());
                  if (o instanceof PaymentSucceeded && t == null) {
                    // do nothing, will be handled by PaymentSent event...
                  } else {
                    String lastErrorCause = null;
                    String detailedErrorMessage = null;
                    if (o instanceof PaymentFailed) {
                      final Seq<PaymentFailure> failures = ((PaymentFailed) o).failures();
                      if (failures.size() > 0) {
                        detailedErrorMessage = generateDetailedErrorCause(failures).toString();
                      }
                    } else if (t != null) {
                      Log.d(TAG, "Error when sending payment", t);
                      lastErrorCause = t.getMessage();
                    }
                    if (!PaymentStatus.PAID.equals(paymentInDB.getStatus())) {
                      // if the payment has not already been paid, lets update the status...
                      paymentInDB.setStatus(PaymentStatus.FAILED);
                    }
                    EventBus.getDefault().post(new LNPaymentFailedEvent(p, lastErrorCause, detailedErrorMessage));
                    app.getDBHelper().insertOrUpdatePayment(paymentInDB);
                  }
                }
              }
            }
          };

          // 3 - execute payment future
          app.sendLNPayment(45, onComplete, amountMsat, pr.paymentHash(), pr.nodeId());
        }
      }
    );
  }

  private void sendBitcoinPayment(final Coin amount, final Coin feesPerKb) {
    Log.d(TAG, "Sending Bitcoin payment for invoice " + mBitcoinInvoice.toString());
    try {
      final SendRequest request = SendRequest.to(mBitcoinInvoice.getAddress(), amount);
      request.feePerKb = feesPerKb;
      app.sendBitcoinPayment(request);
      Toast.makeText(this, R.string.payment_toast_sentbtc, Toast.LENGTH_SHORT).show();
    } catch (InsufficientMoneyException e) {
      Toast.makeText(this, R.string.payment_toast_balance, Toast.LENGTH_LONG).show();
    } catch (Throwable t) {
      Toast.makeText(this, R.string.payment_toast_failure, Toast.LENGTH_LONG).show();
      Log.e(TAG, "Could not send Bitcoin payment", t);
    }
  }

  private void toggleButtons() {
    if (isProcessingPayment) {
      mButtonsView.setVisibility(View.GONE);
    } else {
      mButtonsView.setVisibility(View.VISIBLE);
    }
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    toggleButtons();
  }

  private StringBuilder generateDetailedErrorCause(final Seq<PaymentFailure> failures) {
    final StringBuilder sbErrors = new StringBuilder().append("<p><b>").append(failures.size()).append(" attempt(s) made.</b></p>").append("<small><ul>");
    for (int i = 0; i < failures.size(); i++) {
      final PaymentFailure f = failures.apply(i);
      sbErrors.append("<li>&nbsp;&nbsp;<b>Attempt ").append(i + 1).append(" of ").append(failures.size());
      if (f instanceof RemoteFailure) {
        final RemoteFailure rf = (RemoteFailure) f;
        sbErrors.append(": Remote failure</b>");
        if (rf.route().size() > 0) {
          final scala.collection.immutable.List<Hop> hops = rf.route().toList();
          sbErrors.append(html_error_new_line_bullet).append(" Route (").append(hops.size()).append(" hops):");
          for (int hi = 0; hi < hops.size(); hi++) {
            Hop h = hops.apply(hi);
            if (hi == 0)
              sbErrors.append(html_error_new_line_bullet_inner).append(h.nodeId().toString());
            sbErrors.append(html_error_new_line_bullet_inner).append(h.nextNodeId().toString());
          }
        }
        sbErrors.append(html_error_new_line_bullet).append(" Origin: ").append(rf.e().originNode().toString());
        FailureMessage rfMessage = rf.e().failureMessage();
        if (rfMessage != null) {
          sbErrors.append(html_error_new_line_bullet).append(" Cause: ").append(rfMessage.getClass().getSimpleName());
        }
        sbErrors.append("</li>");
      } else if (f instanceof LocalFailure) {
        final LocalFailure lf = (LocalFailure) f;
        sbErrors.append(": Local failure</b>");
        if (lf.t() instanceof ChannelException) {
          sbErrors.append(html_error_new_line_bullet).append(" Origin: ")
            .append(((ChannelException) lf.t()).getChannelId());
        }
        sbErrors.append(html_error_new_line_bullet).append(" Cause: ").append(((LocalFailure) f).t().getClass().getSimpleName()).append("</li>");
      } else {
        sbErrors.append(": No information available</b></li>");
      }
    }
    sbErrors.append("</ul></small>");
    return sbErrors;
  }
}
