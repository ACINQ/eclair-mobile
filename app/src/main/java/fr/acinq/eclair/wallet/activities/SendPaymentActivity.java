package fr.acinq.eclair.wallet.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.greenrobot.eventbus.util.AsyncExecutor;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.payment.PaymentLifecycle;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.EclairEventService;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.fragments.PinDialog;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDirection;
import fr.acinq.eclair.wallet.models.PaymentStatus;
import fr.acinq.eclair.wallet.models.PaymentType;
import fr.acinq.eclair.wallet.tasks.BitcoinInvoiceReaderTask;
import fr.acinq.eclair.wallet.tasks.LNInvoiceReaderTask;
import fr.acinq.eclair.wallet.utils.BitcoinURI;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import scala.util.Either;

public class SendPaymentActivity extends EclairActivity
  implements LNInvoiceReaderTask.AsyncInvoiceReaderTaskResponse, BitcoinInvoiceReaderTask.AsyncInvoiceReaderTaskResponse {

  public static final String EXTRA_INVOICE = BuildConfig.APPLICATION_ID + "EXTRA_INVOICE";
  private static final String TAG = "SendPayment";
  private final static List<String> LIGHTNING_PREFIXES = Arrays.asList("lightning:", "lightning://");

  private boolean isProcessingPayment = false;
  private PaymentRequest mLNInvoice = null;
  private BitcoinURI mBitcoinInvoice = null;
  private String mInvoice = null;
  private boolean isAmountReadonly = true;

  private TextView mLoadingTextView;
  private View mFormView;
  private View mDescriptionView;
  private TextView mDescriptionValue;
  private View mRecipientView;
  private TextView mRecipientValue;
  private View mPaymentErrorView;
  private TextView mPaymentErrorTextView;
  private TextWatcher amountTextWatcher;
  private TextView mAmountEditableHint;
  private EditText mAmountEditableValue;
  private TextView mAmountEditableUnit;
  private TextView mAmountFiatView;
  private View mPaymentTypeOnchainView;
  private View mPaymentTypeLightningView;
  private View mFeesOnchainView;
  private EditText mFeesValue;
  private Button mFeesButton;
  private TextView mFeesWarning;
  private View mButtonsView;
  private Button mSendButton;
  private Button mCancelButton;

  private CoinUnit preferredBitcoinUnit = CoinUtils.getUnitFromString("btc");
  private String preferredFiatCurrency = Constants.FIAT_USD;
  private boolean maxFeeLightning = true;
  private int maxFeeLightningValue = 1;
  private PinDialog pinDialog;

  @SuppressLint("SetTextI18n")
  @Override
  public void processLNInvoiceFinish(final PaymentRequest output) {
    if (output == null) {
      // try reading invoice as a bitcoin uri
      new BitcoinInvoiceReaderTask(this, mInvoice).execute();
    } else {
      // check lightning channels status
      if (EclairEventService.getChannelsMap().size() == 0) {
        canNotHandlePayment(R.string.payment_error_amount_ln_no_channels);
        return;
      } else if (!EclairEventService.hasActiveChannels()) {
        canNotHandlePayment(R.string.payment_error_amount_ln_no_active_channels);
        return;
      } else {
        final Payment paymentInDB = app.getDBHelper().getPayment(output.paymentHash().toString(), PaymentType.BTC_LN);
        if (paymentInDB != null && (paymentInDB.getStatus() == PaymentStatus.PENDING || paymentInDB.getStatus() == PaymentStatus.INIT)) {
          canNotHandlePayment(R.string.payment_error_pending);
          return;
        } else if (paymentInDB != null && paymentInDB.getStatus() == PaymentStatus.PAID) {
          canNotHandlePayment(R.string.payment_error_paid);
          return;
        }
      }
      mLNInvoice = output;
      isAmountReadonly = mLNInvoice.amount().isDefined();
      if (isAmountReadonly) {
        final MilliSatoshi amountMsat = WalletUtils.getAmountFromInvoice(mLNInvoice);
        if (!EclairEventService.hasActiveChannelsWithBalance(amountMsat.amount())) {
          canNotHandlePayment(R.string.payment_error_amount_ln_insufficient_funds);
          return;
        }
        mAmountEditableHint.setVisibility(View.GONE);
        mAmountEditableValue.setText(CoinUtils.formatAmountInUnit(amountMsat, preferredBitcoinUnit, false));
        mAmountFiatView.setText(WalletUtils.convertMsatToFiatWithUnit(amountMsat.amount(), preferredFiatCurrency));
        disableAmountInteractions();
      } else {
        mAmountEditableValue.addTextChangedListener(amountTextWatcher);
      }
      mRecipientValue.setText(output.nodeId().toBin().toString());
      Either<String, BinaryData> desc = output.description();
      mDescriptionValue.setText(desc.isLeft() ? desc.left().get() : desc.right().get().toString());
      invoiceReadSuccessfully(true);
    }
  }

  private void disableAmountInteractions() {
    mAmountEditableValue.setEnabled(false);
    mAmountEditableValue.setOnClickListener(null);
  }

  @Override
  public void processBitcoinInvoiceFinish(final BitcoinURI output) {
    if (output == null || output.getAddress() == null) {
      canNotHandlePayment(R.string.payment_invalid_address);
    } else if (!app.checkAddressParameters(output.getAddress())) {
      canNotHandlePayment(R.string.payment_invalid_address);
    } else {
      mBitcoinInvoice = output;
      isAmountReadonly = mBitcoinInvoice.getAmount() != null;
      if (isAmountReadonly) {
        final MilliSatoshi amountMsat = package$.MODULE$.satoshi2millisatoshi(mBitcoinInvoice.getAmount());
        mAmountEditableHint.setVisibility(View.GONE);
        mAmountEditableValue.setText(CoinUtils.formatAmountInUnit(amountMsat, preferredBitcoinUnit, false));
        mAmountFiatView.setText(WalletUtils.convertMsatToFiatWithUnit(amountMsat.amount(), preferredFiatCurrency));
        disableAmountInteractions();
      } else {
        mAmountEditableValue.addTextChangedListener(amountTextWatcher);
      }
      setFeesDefault();
      mRecipientValue.setText(output.getAddress());
      invoiceReadSuccessfully(false);
    }
  }

  private void canNotHandlePayment(final int causeMessageId) {
    mLoadingTextView.setTextIsSelectable(true);
    mLoadingTextView.setText(causeMessageId);
  }

  /**
   * Displays the various fields in the payment form, depending on the payment type.
   */
  private void invoiceReadSuccessfully(final boolean isLightning) {
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
    if (!isAmountReadonly) {
      InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      if (inputMethodManager != null) {
        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
        mAmountEditableValue.requestFocus();
      }
    }
  }

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_send_payment);

    final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
    preferredBitcoinUnit = WalletUtils.getPreferredCoinUnit(sharedPref);
    preferredFiatCurrency = WalletUtils.getPreferredFiat(sharedPref);
    maxFeeLightning = sharedPref.getBoolean(Constants.SETTING_LIGHTNING_MAX_FEE, true);
    maxFeeLightningValue = sharedPref.getInt(Constants.SETTING_LIGHTNING_MAX_FEE_VALUE, 1);

    mFormView = findViewById(R.id.payment_form);
    mLoadingTextView = findViewById(R.id.payment_loading);

    // --- static description and recipient displays
    mDescriptionView = findViewById(R.id.payment_description);
    mDescriptionValue = findViewById(R.id.payment_description_value);
    mRecipientView = findViewById(R.id.payment_recipient);
    mRecipientValue = findViewById(R.id.payment_recipient_value);

    // --- amounts
    mAmountEditableHint = findViewById(R.id.payment_amount_editable_hint);
    mAmountEditableUnit = findViewById(R.id.payment_amount_editable_unit);
    mAmountEditableUnit.setText(preferredBitcoinUnit.shortLabel());
    mAmountEditableValue = findViewById(R.id.payment_amount_editable_value);
    mAmountFiatView = findViewById(R.id.payment_amount_fiat);
    amountTextWatcher = new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }
      @SuppressLint("SetTextI18n")
      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        // toggle hint depending on amount input
        mAmountEditableHint.setVisibility(s == null || s.length() == 0 ? View.VISIBLE : View.GONE);
        // display amount in fiat
        try {
          final MilliSatoshi amountMsat = CoinUtils.convertStringAmountToMsat(s.toString(), preferredBitcoinUnit.code());
          mAmountFiatView.setText(WalletUtils.convertMsatToFiatWithUnit(amountMsat.amount(), preferredFiatCurrency));

          if (mBitcoinInvoice != null) {
            if (package$.MODULE$.millisatoshi2satoshi(amountMsat).$greater(app.onChainBalance.get())) {
              handlePaymentError(R.string.payment_error_amount_onchain_insufficient_funds);
            } else {
              mPaymentErrorView.setVisibility(View.GONE);
            }
          }

        } catch (Exception e) {
          Log.e(TAG, "Could not read amount with cause=" + e.getMessage());
          mAmountFiatView.setText("0 " + preferredFiatCurrency.toUpperCase());
        }
      }
      @Override
      public void afterTextChanged(final Editable s) {
      }
    };

    // --- payment type
    mPaymentTypeOnchainView = findViewById(R.id.payment_type_onchain);
    mPaymentTypeLightningView = findViewById(R.id.payment_type_lightning);

    // --- errors display
    mPaymentErrorView = findViewById(R.id.payment_error);
    mPaymentErrorTextView = findViewById(R.id.payment_error_text);

    // --- fees display
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
          final Long feesSatPerByte = Long.parseLong(s.toString());
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

    // --- actions
    mButtonsView = findViewById(R.id.payment_layout_buttons);
    mSendButton = findViewById(R.id.payment_btn_send);
    mCancelButton = findViewById(R.id.payment_btn_cancel);

    // --- read invoice from intent
    final Intent intent = getIntent();
    mInvoice = intent.getStringExtra(EXTRA_INVOICE).trim();
    Log.d(TAG, "Initializing payment with invoice=" + mInvoice);
    if (mInvoice != null) {
      for (String prefix : LIGHTNING_PREFIXES) {
        if (mInvoice.toLowerCase().startsWith(prefix)) {
          mInvoice = mInvoice.substring(prefix.length());
          break;
        }
      }
      new LNInvoiceReaderTask(this, mInvoice).execute();
    } else {
      canNotHandlePayment(R.string.payment_invalid_address);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    checkInit();
  }

  public void focusAmount(final View view) {
    mAmountEditableValue.requestFocus();
  }

  @SuppressLint("SetTextI18n")
  public void pickFees(final View view) {
    try {
      final Long feesSatPerByte = Long.parseLong(mFeesValue.getText().toString());
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
    closeAndGoHome();
  }

  private void closeAndGoHome() {
    Intent intent = new Intent(getBaseContext(), HomeActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    startActivity(intent);
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
          ? WalletUtils.getLongAmountFromInvoice(mLNInvoice)
          : CoinUtils.convertStringAmountToMsat(mAmountEditableValue.getText().toString(), preferredBitcoinUnit.code()).amount();
        if (isPinRequired()) {
          pinDialog = new PinDialog(SendPaymentActivity.this, R.style.CustomAlertDialog, new PinDialog.PinDialogCallback() {
            @Override
            public void onPinConfirm(final PinDialog dialog, final String pinValue) {
              if (isPinCorrect(pinValue, dialog)) {
                sendLNPayment(amountMsat, mLNInvoice, mInvoice);
                closeAndGoHome();
              } else {
                handlePaymentError(R.string.payment_error_incorrect_pin);
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
          closeAndGoHome();
        }
      } else if (mBitcoinInvoice != null) {
        final Satoshi amountSat = isAmountReadonly
          ? mBitcoinInvoice.getAmount()
          : CoinUtils.convertStringAmountToSat(mAmountEditableValue.getText().toString(), preferredBitcoinUnit.code());
        if (amountSat.$greater(app.onChainBalance.get())) {
          handlePaymentError(R.string.payment_error_amount_onchain_insufficient_funds);
          return;
        }
        try {
          final Long feesPerKw = fr.acinq.eclair.package$.MODULE$.feerateByte2Kw(Long.parseLong(mFeesValue.getText().toString()));
          if (isPinRequired()) {
            pinDialog = new PinDialog(SendPaymentActivity.this, R.style.CustomAlertDialog, new PinDialog.PinDialogCallback() {
              public void onPinConfirm(final PinDialog dialog, final String pinValue) {
                if (isPinCorrect(pinValue, dialog)) {
                  sendBitcoinPayment(amountSat, feesPerKw, mBitcoinInvoice);
                  closeAndGoHome();
                } else {
                  handlePaymentError(R.string.payment_error_incorrect_pin);
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
            sendBitcoinPayment(amountSat, feesPerKw, mBitcoinInvoice);
            closeAndGoHome();
          }
        } catch (NumberFormatException e) {
          handlePaymentError(R.string.payment_error_fees_onchain);
        }
      }
    } catch (NumberFormatException e) {
      handlePaymentError(R.string.payment_error_amount);
    } catch (Exception e) {
      Log.e(TAG, "Could not send payment", e);
      handlePaymentError(R.string.payment_error);
    }
  }

  /**
   * Displays an error message when a payment has failed.
   *
   * @param messageId resource id of the the message
   */
  private void handlePaymentError(final int messageId) {
    isProcessingPayment = false;
    toggleForm();
    mPaymentErrorTextView.setText(getString(messageId));
    mPaymentErrorView.setVisibility(View.VISIBLE);
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
   * @param pr         lightning payment request
   * @param prAsString payment request as a string (used for display)
   */
  private void sendLNPayment(final long amountMsat, final PaymentRequest pr, final String prAsString) {
    Log.d(TAG, "Sending LN payment for invoice " + prAsString);
    AsyncExecutor.create().execute(
      () -> {
        // 0 - Check if payment already exists
        final String paymentHash = pr.paymentHash().toString();
        final String paymentDescription = pr.description().isLeft() ? pr.description().left().get() : pr.description().right().get().toString();
        Payment p = app.getDBHelper().getPayment(paymentHash, PaymentType.BTC_LN);

        // payment attempt is processed if it does not already exist or is not failed
        if (p != null && p.getStatus() != PaymentStatus.FAILED) {
          Log.d(TAG, "payment " + paymentHash+ " aborted");
          return;
        } else if (p == null) {
          p = new Payment();
          p.setType(PaymentType.BTC_LN);
          p.setDirection(PaymentDirection.SENT);
          p.setReference(paymentHash);
          p.setAmountRequestedMsat(WalletUtils.getLongAmountFromInvoice(pr));
          p.setRecipient(pr.nodeId().toString());
          p.setPaymentRequest(prAsString.toLowerCase());
          p.setStatus(PaymentStatus.INIT);
          p.setDescription(paymentDescription);
          p.setUpdated(new Date());
          app.getDBHelper().insertOrUpdatePayment(p);
        }

        Long minFinalCltvExpiry = PaymentLifecycle.defaultMinFinalCltvExpiry();
        if (pr.minFinalCltvExpiry().isDefined() && pr.minFinalCltvExpiry().get() instanceof Long) {
          minFinalCltvExpiry = (Long) pr.minFinalCltvExpiry().get();
        }
        // execute payment future
        app.sendLNPayment(45, amountMsat, pr.paymentHash(), pr.nodeId(), minFinalCltvExpiry);
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
    Log.d(TAG, "Sending Bitcoin payment for invoice " + mBitcoinInvoice.toString() + " with amount = " + amountSat);
    app.sendBitcoinPayment(amountSat, bitcoinURI.getAddress(), feesPerKw);
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
