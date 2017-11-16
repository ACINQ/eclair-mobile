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

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.util.AsyncExecutor;

import java.util.Date;

import akka.dispatch.OnComplete;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.MilliBtc;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.Transaction;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.channel.ChannelException;
import fr.acinq.eclair.payment.Hop;
import fr.acinq.eclair.payment.LocalFailure;
import fr.acinq.eclair.payment.PaymentFailed;
import fr.acinq.eclair.payment.PaymentFailure;
import fr.acinq.eclair.payment.PaymentLifecycle;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.payment.PaymentSucceeded;
import fr.acinq.eclair.payment.RemoteFailure;
import fr.acinq.eclair.wallet.EclairEventService;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.customviews.CoinAmountView;
import fr.acinq.eclair.wallet.events.BitcoinPaymentEvent;
import fr.acinq.eclair.wallet.events.LNPaymentFailedEvent;
import fr.acinq.eclair.wallet.fragments.PinDialog;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDirection;
import fr.acinq.eclair.wallet.models.PaymentStatus;
import fr.acinq.eclair.wallet.models.PaymentType;
import fr.acinq.eclair.wallet.tasks.BitcoinInvoiceReaderTask;
import fr.acinq.eclair.wallet.tasks.LNInvoiceReaderTask;
import fr.acinq.eclair.wallet.utils.BitcoinURI;
import fr.acinq.eclair.wallet.utils.CoinUtils;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wire.FailureMessage;
import scala.collection.Seq;
import scala.collection.mutable.StringBuilder;
import scala.concurrent.Future;
import scala.math.BigDecimal;
import scala.util.Either;

public class CreatePaymentActivity extends EclairActivity
  implements LNInvoiceReaderTask.AsyncInvoiceReaderTaskResponse, BitcoinInvoiceReaderTask.AsyncInvoiceReaderTaskResponse {

  public static final String EXTRA_INVOICE = "fr.acinq.eclair.wallet.EXTRA_INVOICE";
  private static final String TAG = "CreatePayment";
  private final static String html_error_tab = "&nbsp;&nbsp;&nbsp;&nbsp;";
  private final static String html_error_new_line = "<br />" + html_error_tab;
  private final static String html_error_new_line_bullet = html_error_new_line + "&#9679;&nbsp;&nbsp;";
  private final static String html_error_new_line_bullet_inner = html_error_new_line + html_error_tab + "&#9679;&nbsp;&nbsp;";
  private boolean isProcessingPayment = false;
  private PaymentRequest mLNInvoice = null;
  private BitcoinURI mBitcoinInvoice = null;
  private String mInvoice = null;
  private TextView mLoadingTextView;
  private View mFormView;
  private CoinAmountView mAmountReadonlyValue;
  private View mDescriptionView;
  private TextView mDescriptionValue;
  private View mRecipientView;
  private TextView mRecipientValue;
  private View mPaymentErrorView;
  private TextView mPaymentErrorTextView;
  private View mAmountEditableView;
  private TextView mAmountEditableHint;
  private EditText mAmountEditableValue;
  private TextView mAmountFiatView;
  private View mPaymentTypeOnchainView;
  private View mPaymentTypeLightningView;
  private boolean isAmountReadonly = true;
  private View mFeesOnchainView;
  private EditText mFeesValue;
  private Button mFeesButton;
  private TextView mFeesWarning;
  private View mButtonsView;
  private Button mSendButton;
  private Button mCancelButton;

  private String preferredFiatCurrency;
  private boolean maxFeeLightning = true;
  private int maxFeeLightningValue = 1;
  private PinDialog pinDialog;

  private static StringBuilder generateDetailedErrorCause(final Seq<PaymentFailure> failures) {
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
            if (hi == 0) {
              sbErrors.append(html_error_new_line_bullet_inner).append(h.nodeId().toString());
            }
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

  @SuppressLint("SetTextI18n")
  @Override
  public void processLNInvoiceFinish(final PaymentRequest output) {
    if (output == null) {
      // try reading invoice as a bitcoin uri
      new BitcoinInvoiceReaderTask(this, mInvoice).execute();
    } else {
      // check if the user has any channels
      if (EclairEventService.getChannelsMap().size() == 0) {
        handlePaymentError(R.string.payment_error_amount_ln_no_channels, true);
      }
      mLNInvoice = output;
      isAmountReadonly = mLNInvoice.amount().isDefined();
      setAmountViewVisibility();
      if (isAmountReadonly) {
        final MilliSatoshi amountMsat = CoinUtils.getAmountFromInvoice(mLNInvoice);
        mAmountReadonlyValue.setAmountMsat(amountMsat);
        setFiatAmount(amountMsat);
      }
      mRecipientValue.setText(output.nodeId().toBin().toString());
      Either<String, BinaryData> desc = output.description();
      mDescriptionValue.setText(desc.isLeft() ? desc.left().get() : desc.right().get().toString());
      invoiceReadSuccessfully(true);
    }
  }

  @Override
  public void processBitcoinInvoiceFinish(final BitcoinURI output) {
    if (output == null || output.getAddress() == null) {
      couldNotReadInvoice(R.string.payment_failure_read_invoice);
    } else if (!app.checkAddressParameters(output.getAddress())) {
      couldNotReadInvoice(R.string.payment_invalid_address);
    } else {
      this.app.requestOnchainBalanceUpdate();
      mBitcoinInvoice = output;
      isAmountReadonly = mBitcoinInvoice.getAmount() != null;
      if (isAmountReadonly) {
        final MilliSatoshi amountMsat = package$.MODULE$.satoshi2millisatoshi(mBitcoinInvoice.getAmount());
        mAmountReadonlyValue.setAmountMsat(amountMsat);
        setFiatAmount(amountMsat);
      }
      setFeesDefault();
      mRecipientValue.setText(output.getAddress());
      invoiceReadSuccessfully(false);
    }
  }

  /**
   * Converts the value of the payment's amount to the preferred fiat currency and updates the UI.
   *
   * @param amountMsat
   */
  @SuppressLint("SetTextI18n")
  private void setFiatAmount(final MilliSatoshi amountMsat) {
    Double rate = preferredFiatCurrency.equals("eur") ? app.getEurRate() : app.getUsdRate();
    final Double amountEur = package$.MODULE$.millisatoshi2btc(amountMsat).amount().doubleValue() * rate;
    mAmountReadonlyValue.setAmountMsat(amountMsat);
    mAmountFiatView.setText(CoinUtils.getFiatFormat().format(amountEur) + " " + preferredFiatCurrency.toUpperCase());
  }

  private void couldNotReadInvoice(final int causeMessageId) {
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

  /**
   * Displays the various fields in the payment form, depending on the payment type.
   */
  private void invoiceReadSuccessfully(final boolean isLightning) {
    setAmountViewVisibility();
    if (isLightning) {
      mFeesOnchainView.setVisibility(View.GONE);
      mPaymentTypeOnchainView.setVisibility(View.GONE);
      mPaymentTypeLightningView.setVisibility(View.VISIBLE);
      mDescriptionView.setVisibility(View.VISIBLE);
      mRecipientView.setVisibility(View.VISIBLE);
    } else {
      mFeesOnchainView.setVisibility(View.VISIBLE);
      mPaymentTypeOnchainView.setVisibility(View.VISIBLE);
      mPaymentTypeLightningView.setVisibility(View.GONE);
      mDescriptionView.setVisibility(View.GONE);
      mRecipientView.setVisibility(View.VISIBLE);
    }
    // display form
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
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_create_payment);

    final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
    preferredFiatCurrency = sharedPref.getString(Constants.SETTING_SELECTED_FIAT_CURRENCY, "eur");
    maxFeeLightning = sharedPref.getBoolean(Constants.SETTING_LIGHTNING_MAX_FEE, true);
    maxFeeLightningValue = sharedPref.getInt(Constants.SETTING_LIGHTNING_MAX_FEE_VALUE, 1);

    mFormView = findViewById(R.id.payment_form);
    mLoadingTextView = findViewById(R.id.payment_loading);
    mDescriptionView = findViewById(R.id.payment_description);
    mDescriptionValue = findViewById(R.id.payment_description_value);
    mRecipientView = findViewById(R.id.payment_recipient);
    mRecipientValue = findViewById(R.id.payment_recipient_value);
    mAmountReadonlyValue = findViewById(R.id.payment_amount_readonly_value);
    mAmountEditableView = findViewById(R.id.payment_amount_editable);
    mAmountEditableHint = findViewById(R.id.payment_amount_editable_hint);
    mAmountEditableValue = findViewById(R.id.payment_amount_editable_value);
    mAmountEditableValue.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        // toggle hint depending on amount input
        mAmountEditableHint.setVisibility(s == null || s.length() == 0 ? View.VISIBLE : View.GONE);
        try {
          final BigDecimal amount = BigDecimal.exact(s.toString());
          setFiatAmount(package$.MODULE$.millibtc2millisatoshi(new MilliBtc(amount)));
        } catch (NumberFormatException e) {
          Log.d(TAG, "Could not read amount with cause=" + e.getMessage());
          setFiatAmount(new MilliSatoshi(0));
        }
      }

      @Override
      public void afterTextChanged(final Editable s) {
      }
    });
    mAmountFiatView = findViewById(R.id.payment_amount_fiat);
    mPaymentTypeOnchainView = findViewById(R.id.payment_type_onchain);
    mPaymentTypeLightningView = findViewById(R.id.payment_type_lightning);
    mPaymentErrorView = findViewById(R.id.payment_error);
    mPaymentErrorTextView = findViewById(R.id.payment_error_text);
    mFeesOnchainView = findViewById(R.id.payment_fees_onchain);
    mFeesButton = findViewById(R.id.payment_fees_rating);
    mFeesValue = findViewById(R.id.payment_fees_value);
    mFeesWarning = findViewById(R.id.payment_fees_warning);
    mFeesValue.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
      }

      @SuppressLint("SetTextI18n")
      @Override
      public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
        try {
          Long feesSatPerByte = Long.parseLong(s.toString());
          if (feesSatPerByte == app.estimateSlowFees()) {
            mFeesButton.setText(R.string.payment_fees_slow);
          } else if (feesSatPerByte == app.estimateMediumFees()) {
            mFeesButton.setText(R.string.payment_fees_medium);
          } else if (feesSatPerByte == app.estimateFastFees()) {
            mFeesButton.setText(R.string.payment_fees_fast);
          } else {
            mFeesButton.setText(R.string.payment_fees_custom);
          }
          if (feesSatPerByte <= app.estimateSlowFees() / 2) {
            mFeesWarning.setText(R.string.payment_fees_verylow);
            mFeesWarning.setVisibility(View.VISIBLE);
          } else if (feesSatPerByte >= app.estimateFastFees() * 2) {
            mFeesWarning.setText(R.string.payment_fees_veryhigh);
            mFeesWarning.setVisibility(View.VISIBLE);
          } else {
            mFeesWarning.setVisibility(View.GONE);
          }
        } catch (NumberFormatException e) {
          Log.e(TAG, "Could not read fees", e);
        }
      }

      @Override
      public void afterTextChanged(Editable s) {
      }
    });
    mButtonsView = findViewById(R.id.payment_layout_buttons);
    mSendButton = findViewById(R.id.payment_btn_send);
    mCancelButton = findViewById(R.id.payment_btn_cancel);

    Intent intent = getIntent();
    mInvoice = intent.getStringExtra(EXTRA_INVOICE);
    Log.i(TAG, "Initializing payment with invoice=" + mInvoice);
    new LNInvoiceReaderTask(this, mInvoice).execute();
  }

  @SuppressLint("SetTextI18n")
  public void pickFees(final View view) {
    try {
      Long feesSatPerByte = Long.parseLong(mFeesValue.getText().toString());
      if (feesSatPerByte <= app.estimateSlowFees()) {
        mFeesValue.setText(String.valueOf(app.estimateMediumFees()));
      } else if (feesSatPerByte <= app.estimateMediumFees()) {
        mFeesValue.setText(String.valueOf(app.estimateFastFees()));
      } else {
        mFeesValue.setText(String.valueOf(app.estimateSlowFees()));
      }
    } catch (NumberFormatException e) {
      Log.e(TAG, "Could not read fees", e);
      mFeesValue.setText(String.valueOf(app.estimateSlowFees()));
    }
  }

  @SuppressLint("SetTextI18n")
  private void setFeesDefault() {
    mFeesValue.setText(String.valueOf(app.estimateFastFees()));
  }

  public void cancelPayment(View view) {
    finish();
  }

  /**
   * Prepare the execution of the current payment request stored in the activity, be it an on-chain payment or a lightning payment.
   * Opens a PIN dialog to confirm the payment. If the PIN is correct the payment is executed.
   */
  public void confirmPayment(final View view) {

    // Stop if a payment is already being processed
    if (isProcessingPayment) return;

    // Update visuals
    isProcessingPayment = true;
    toggleForm();

    // Get amount and executes payment. Depending on the settings, the user must first enter the correct PIN code
    try {
      if (mLNInvoice != null) {
        final long amountMsat = isAmountReadonly
          ? CoinUtils.getLongAmountFromInvoice(mLNInvoice)
          : package$.MODULE$.satoshi2millisatoshi(CoinUtils.parseMilliSatoshiAmount(mAmountEditableValue.getText().toString())).amount();
        if (EclairEventService.hasActiveChannelsWithBalance(amountMsat)) {
          if (isPinRequired()) {
            pinDialog = new PinDialog(CreatePaymentActivity.this, R.style.CustomAlertDialog, new PinDialog.PinDialogCallback() {
              @Override
              public void onPinConfirm(final PinDialog dialog, final String pinValue) {
                if (isPinCorrect(pinValue, dialog)) {
                  sendLNPayment(amountMsat, mLNInvoice, mInvoice);
                  finish();
                } else {
                  handlePaymentError(R.string.payment_error_incorrect_pin, false);
                }
              }

              @Override
              public void onPinCancel(PinDialog dialog) {
                isProcessingPayment = false;
                toggleForm();
              }
            });
            pinDialog.show();
          } else {
            sendLNPayment(amountMsat, mLNInvoice, mInvoice);
            finish();
          }
        } else {
          if (EclairEventService.hasActiveChannels()) {
            // Refine the error message to guide the user: he does not have enough balance on any of the channels
            handlePaymentError(R.string.payment_error_amount_ln_insufficient_funds, true);
          } else {
            // The user simply does not have any active channels
            handlePaymentError(R.string.payment_error_amount_ln_no_active_channels, true);
          }
        }
      } else if (mBitcoinInvoice != null) {
        final Satoshi amount = isAmountReadonly ? mBitcoinInvoice.getAmount() : CoinUtils.parseMilliSatoshiAmount(mAmountEditableValue.getText().toString());
        try {
          final Long feesPerKw = fr.acinq.eclair.package$.MODULE$.feerateByte2Kw(Long.parseLong(mFeesValue.getText().toString()));
          if (isPinRequired()) {
            pinDialog = new PinDialog(CreatePaymentActivity.this, R.style.CustomAlertDialog, new PinDialog.PinDialogCallback() {
              public void onPinConfirm(final PinDialog dialog, final String pinValue) {
                if (isPinCorrect(pinValue, dialog)) {
                  sendBitcoinPayment(amount, feesPerKw, mBitcoinInvoice);
                  finish();
                } else {
                  handlePaymentError(R.string.payment_error_incorrect_pin, false);
                }
              }

              @Override
              public void onPinCancel(final PinDialog dialog) {
                isProcessingPayment = false;
                toggleForm();
              }
            });
            pinDialog.show();
          } else {
            sendBitcoinPayment(amount, feesPerKw, mBitcoinInvoice);
            finish();
          }
        } catch (NumberFormatException e) {
          handlePaymentError(R.string.payment_error_fees_onchain, false);
        }
      }
    } catch (NumberFormatException e) {
      handlePaymentError(R.string.payment_error_amount, false);
    } catch (Exception e) {
      Log.e(TAG, "Could not send payment", e);
      handlePaymentError(R.string.payment_error, false);
    }
  }

  /**
   * Displays an error message when a payment has failed.
   *
   * @param messageId resource id of the the message
   * @param isHtml
   */
  private void handlePaymentError(final int messageId, final boolean isHtml) {
    isProcessingPayment = false;
    toggleForm();
    mPaymentErrorView.setVisibility(View.VISIBLE);
    if (isHtml) {
      mPaymentErrorTextView.setText(Html.fromHtml(getString(messageId)));
    } else {
      mPaymentErrorTextView.setText(messageId);
    }
  }

  @Override
  protected void onPause() {
    // dismiss the pin dialog if it exists to prevent leak.
    if (pinDialog != null) {
      pinDialog.dismiss();
    }
    super.onPause();
  }

  /**
   * Executes a Lightning payment in an asynchronous task.
   *
   * @param amountMsat amount of the payment in milli satoshis
   * @param pr         Lightning payment request
   * @param prAsString payment request as a string (used for display)
   */
  private final void sendLNPayment(final long amountMsat, final PaymentRequest pr, final String prAsString) {
    Log.d(TAG, "Sending LN payment for invoice " + prAsString);
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

          int minFinalCltvExpiry = PaymentLifecycle.defaultMinFinalCltvExpiry();
          if (pr.minFinalCltvExpiry().isDefined() && pr.minFinalCltvExpiry().get() instanceof Integer) {
            minFinalCltvExpiry = (Integer) pr.minFinalCltvExpiry().get();
          }
          // 3 - execute payment future
          app.sendLNPayment(45, onComplete, amountMsat, pr.paymentHash(), pr.nodeId(), minFinalCltvExpiry);
        }
      }
    );
  }

  /**
   * Sends a Bitcoin transaction.
   *
   * @param amountSat  amount of the tx in satoshis
   * @param feesPerKw  fees to the network in satoshis per kb
   * @param bitcoinURI contains the bitcoin address
   */
  private void sendBitcoinPayment(final Satoshi amountSat, final Long feesPerKw, final BitcoinURI bitcoinURI) {
    Log.d(TAG, "Sending Bitcoin payment for invoice " + mBitcoinInvoice.toString());
    try {
      final CreatePaymentActivity context = this;
      Future fBitcoinPayment = app.getWallet().sendPayment(amountSat, bitcoinURI.getAddress(), feesPerKw);
      fBitcoinPayment.onComplete(new OnComplete<String>() {
        @Override
        public void onComplete(final Throwable t, final String txId) {
          if (t == null) {
            // insert tx in db
            final Payment txAsPayment = new Payment();
            txAsPayment.setType(PaymentType.BTC_ONCHAIN);
            txAsPayment.setDirection(PaymentDirection.SENT);
            txAsPayment.setReference(txId);
            txAsPayment.setConfidenceType(0);
            txAsPayment.setUpdated(new Date());
            app.getDBHelper().insertOrUpdatePayment(txAsPayment);
            EventBus.getDefault().post(new BitcoinPaymentEvent(txAsPayment));
          } else {
            Log.e(TAG, "Could not send Bitcoin tx", t);
            context.runOnUiThread(new Runnable() {
              public void run() {
                Toast.makeText(context, R.string.payment_toast_failure, Toast.LENGTH_LONG).show();
              }
            });
          }
        }
      }, app.system.dispatcher());
    } catch (Throwable t) {
      Log.e(TAG, "Could not send Bitcoin tx", t);
      Toast.makeText(getApplicationContext(), R.string.payment_toast_failure, Toast.LENGTH_LONG).show();
    }
  }

  /**
   * Handle the visibility and interactivity of form's elements according to the state of the payment.
   * If the payment is being processed (or the PIN dialog is shown) editable inputs and buttons are disabled.
   */
  private void toggleForm() {
    if (isProcessingPayment) {
      mAmountEditableValue.setEnabled(false);
      mFeesValue.setEnabled(false);
      mFeesButton.setEnabled(false);
      mSendButton.setEnabled(false);
      mCancelButton.setEnabled(false);
      mButtonsView.setAlpha(0.3f);
      mPaymentErrorView.setVisibility(View.GONE);
    } else {
      mAmountEditableValue.setEnabled(true);
      mFeesValue.setEnabled(true);
      mFeesButton.setEnabled(true);
      mSendButton.setEnabled(true);
      mCancelButton.setEnabled(true);
      mButtonsView.setAlpha(1);
    }
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    toggleForm();
  }

}
