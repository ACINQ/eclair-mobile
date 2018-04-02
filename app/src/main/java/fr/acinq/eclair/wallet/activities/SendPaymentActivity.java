/*
 * Copyright 2018 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.eclair.wallet.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

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
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.EclairEventService;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivitySendPaymentBinding;
import fr.acinq.eclair.wallet.fragments.PinDialog;
import fr.acinq.eclair.wallet.models.FeeRating;
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

  private ActivitySendPaymentBinding mBinding;

  private CoinUnit preferredBitcoinUnit = CoinUtils.getUnitFromString("btc");
  private String preferredFiatCurrency = Constants.FIAT_USD;
  // state of the fees, used with data binding
  private FeeRating feeRatingState = Constants.FEE_RATING_FAST;
  private boolean capLightningFees = true;
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
        canNotHandlePayment(R.string.payment_error_ln_no_channels);
        return;
      } else {
        final Payment paymentInDB = app.getDBHelper().getPayment(output.paymentHash().toString(), PaymentType.BTC_LN);
        if (paymentInDB != null && paymentInDB.getStatus() == PaymentStatus.PENDING) {
          canNotHandlePayment(R.string.payment_error_pending);
          return;
        } else if (paymentInDB != null && paymentInDB.getStatus() == PaymentStatus.PAID) {
          canNotHandlePayment(R.string.payment_error_paid);
          return;
        }
      }
      mLNInvoice = output;
      isAmountReadonly = mLNInvoice.amount().isDefined();

      if (!capLightningFees) {
        mBinding.feesWarning.setText(R.string.payment_fees_not_capped);
        mBinding.feesWarning.setVisibility(View.VISIBLE);
      }

      if (isAmountReadonly) {
        final MilliSatoshi amountMsat = WalletUtils.getAmountFromInvoice(mLNInvoice);
        if (!EclairEventService.hasActiveChannelsWithBalance(amountMsat.amount())) {
          canNotHandlePayment(R.string.payment_error_ln_insufficient_funds);
          return;
        }
        mBinding.amountEditableValue.setText(CoinUtils.rawAmountInUnit(amountMsat, preferredBitcoinUnit).bigDecimal().toPlainString());
        mBinding.amountFiat.setText(WalletUtils.convertMsatToFiatWithUnit(amountMsat.amount(), preferredFiatCurrency));
        // the amount can be overridden by the user to reduce information leakage, lightning allows payments to be overpaid
        // see https://github.com/lightningnetwork/lightning-rfc/blob/master/04-onion-routing.md#requirements-2
        // as such, the amount field stays editable.
      }
      mBinding.recipientValue.setText(output.nodeId().toBin().toString());
      Either<String, BinaryData> desc = output.description();
      mBinding.descriptionValue.setText(desc.isLeft() ? desc.left().get() : desc.right().get().toString());
      invoiceReadSuccessfully(true);
    }
  }

  private void disableAmountInteractions() {
    mBinding.amountEditableValue.setEnabled(false);
    mBinding.amountEditableValue.setOnClickListener(null);
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
      mBinding.emptyOnchainWallet.setVisibility(View.VISIBLE);
      if (isAmountReadonly) {
        final MilliSatoshi amountMsat = package$.MODULE$.satoshi2millisatoshi(mBitcoinInvoice.getAmount());
        mBinding.amountEditableHint.setVisibility(View.GONE);
        mBinding.amountEditableValue.setText(CoinUtils.formatAmountInUnit(amountMsat, preferredBitcoinUnit, false));
        mBinding.amountFiat.setText(WalletUtils.convertMsatToFiatWithUnit(amountMsat.amount(), preferredFiatCurrency));
        disableAmountInteractions();
      }
      setFeesToDefault();
      mBinding.recipientValue.setText(output.getAddress());
      invoiceReadSuccessfully(false);
    }
  }

  private void canNotHandlePayment(final int causeMessageId) {
    mBinding.form.setVisibility(View.GONE);
    mBinding.loading.setVisibility(View.VISIBLE);
    mBinding.loading.setTextIsSelectable(true);
    mBinding.loading.setText(causeMessageId);
  }

  /**
   * Displays the various fields in the payment form, depending on the payment type.
   */
  private void invoiceReadSuccessfully(final boolean isLightning) {
    if (isLightning) {
      mBinding.feesOnchain.setVisibility(View.GONE);
      mBinding.typeOnchain.setVisibility(View.GONE);
      mBinding.typeLightning.setVisibility(View.VISIBLE);
      mBinding.description.setVisibility(View.VISIBLE);
      mBinding.recipient.setVisibility(View.VISIBLE);
    } else {
      mBinding.feesOnchain.setVisibility(View.VISIBLE);
      mBinding.typeOnchain.setVisibility(View.VISIBLE);
      mBinding.typeLightning.setVisibility(View.GONE);
      mBinding.description.setVisibility(View.GONE);
      mBinding.recipient.setVisibility(View.VISIBLE);
    }
    // display form
    mBinding.loading.setVisibility(View.GONE);
    mBinding.form.setVisibility(View.VISIBLE);
    if (!isAmountReadonly) {
      InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      if (inputMethodManager != null) {
        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
        mBinding.amountEditableValue.requestFocus();
      }
    }
  }

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_send_payment);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_send_payment);
    mBinding.setFeeRatingState(feeRatingState);

    final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
    preferredBitcoinUnit = WalletUtils.getPreferredCoinUnit(sharedPref);
    preferredFiatCurrency = WalletUtils.getPreferredFiat(sharedPref);
    capLightningFees = sharedPref.getBoolean(Constants.SETTING_CAP_LIGHTNING_FEES, true);
    mBinding.amountEditableUnit.setText(preferredBitcoinUnit.shortLabel());

    mBinding.amountEditableValue.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @SuppressLint("SetTextI18n")
      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        // toggle hint depending on amount input
        mBinding.amountEditableHint.setVisibility(s == null || s.length() == 0 ? View.VISIBLE : View.GONE);
        try {
          final MilliSatoshi amountMsat = CoinUtils.convertStringAmountToMsat(s.toString(), preferredBitcoinUnit.code());
          mBinding.amountFiat.setText(WalletUtils.convertMsatToFiatWithUnit(amountMsat.amount(), preferredFiatCurrency));
          if (mBitcoinInvoice != null) {
            if (package$.MODULE$.millisatoshi2satoshi(amountMsat).$greater(app.onChainBalance.get())) {
              handlePaymentError(R.string.payment_error_amount_onchain_insufficient_funds);
            } else {
              mBinding.paymentError.setVisibility(View.GONE);
            }
          }
        } catch (Exception e) {
          Log.w(TAG, "Could not read amount with cause=" + e.getMessage());
          mBinding.amountFiat.setText("0 " + preferredFiatCurrency.toUpperCase());
        }
      }

      @Override
      public void afterTextChanged(final Editable s) {
      }
    });

    mBinding.feesValue.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
      }

      @SuppressLint("SetTextI18n")
      @Override
      public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
        try {
          final Long feesSatPerByte = Long.parseLong(s.toString());
          if (feesSatPerByte != app.estimateSlowFees() && feesSatPerByte != app.estimateMediumFees() && feesSatPerByte != app.estimateFastFees()) {
            feeRatingState = Constants.FEE_RATING_CUSTOM;
            mBinding.setFeeRatingState(feeRatingState);
          }
          if (feesSatPerByte <= app.estimateSlowFees() / 2) {
            mBinding.feesWarning.setText(R.string.payment_fees_verylow);
            mBinding.feesWarning.setVisibility(View.VISIBLE);
          } else if (feesSatPerByte >= app.estimateFastFees() * 2) {
            mBinding.feesWarning.setText(R.string.payment_fees_veryhigh);
            mBinding.feesWarning.setVisibility(View.VISIBLE);
          } else {
            mBinding.feesWarning.setVisibility(View.GONE);
          }
        } catch (NumberFormatException e) {
          Log.w(TAG, "Could not read fees with cause=" + e.getMessage());
        }
      }

      @Override
      public void afterTextChanged(Editable s) {
      }
    });

    mBinding.emptyOnchainWallet.setOnCheckedChangeListener((buttonView, isChecked) -> {
      if (mBitcoinInvoice != null) {
        if (isChecked) {
          mBinding.emptyWalletDisclaimer.setVisibility(View.VISIBLE);
          mBinding.amountEditableValue.setEnabled(false);
          mBinding.amountEditableValue.setText(CoinUtils.rawAmountInUnit(
            app.onChainBalance.get(), preferredBitcoinUnit).bigDecimal().toPlainString());
        } else {
          mBinding.emptyWalletDisclaimer.setVisibility(View.GONE);
          mBinding.amountEditableValue.setEnabled(true);
        }
      }
    });

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
    mBinding.amountEditableValue.requestFocus();
  }

  public void pickFees(final View view) {
    if (feeRatingState.rating == Constants.FEE_RATING_SLOW.rating) {
      feeRatingState = Constants.FEE_RATING_MEDIUM;
      mBinding.feesValue.setText(String.valueOf(app.estimateMediumFees()));
      mBinding.setFeeRatingState(feeRatingState);
    } else if (feeRatingState.rating == Constants.FEE_RATING_MEDIUM.rating) {
      feeRatingState = Constants.FEE_RATING_FAST;
      mBinding.feesValue.setText(String.valueOf(app.estimateFastFees()));
      mBinding.setFeeRatingState(feeRatingState);
    } else if (feeRatingState.rating == Constants.FEE_RATING_FAST.rating) {
      feeRatingState = Constants.FEE_RATING_SLOW;
      mBinding.feesValue.setText(String.valueOf(app.estimateSlowFees()));
      mBinding.setFeeRatingState(feeRatingState);
    } else {
      setFeesToDefault();
    }
  }

  private void setFeesToDefault() {
    feeRatingState = Constants.FEE_RATING_FAST;
    mBinding.feesValue.setText(String.valueOf(app.estimateFastFees()));
    mBinding.setFeeRatingState(feeRatingState);
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
        if(!EclairEventService.hasActiveChannels()) {
          handlePaymentError(R.string.payment_error_ln_no_active_channels);
          return;
        }
        final long amountMsat = CoinUtils.convertStringAmountToMsat(mBinding.amountEditableValue.getText().toString(), preferredBitcoinUnit.code()).amount();
        if (isPinRequired()) {
          pinDialog = new PinDialog(SendPaymentActivity.this, R.style.CustomAlertDialog, new PinDialog.PinDialogCallback() {
            @Override
            public void onPinConfirm(final PinDialog dialog, final String pinValue) {
              if (isPinCorrect(pinValue, dialog)) {
                sendLNPayment(amountMsat, mLNInvoice, mInvoice);
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
          : CoinUtils.convertStringAmountToSat(mBinding.amountEditableValue.getText().toString(), preferredBitcoinUnit.code());
        if (amountSat.$greater(app.onChainBalance.get())) {
          handlePaymentError(R.string.payment_error_amount_onchain_insufficient_funds);
          return;
        }
        try {
          final Long feesPerKw = fr.acinq.eclair.package$.MODULE$.feerateByte2Kw(Long.parseLong(mBinding.feesValue.getText().toString()));
          final boolean emptyWallet = mBinding.emptyOnchainWallet.isChecked();
          if (isPinRequired()) {
            pinDialog = new PinDialog(SendPaymentActivity.this, R.style.CustomAlertDialog, new PinDialog.PinDialogCallback() {
              public void onPinConfirm(final PinDialog dialog, final String pinValue) {
                if (isPinCorrect(pinValue, dialog)) {
                  sendBitcoinPayment(amountSat, feesPerKw, mBitcoinInvoice, emptyWallet);
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
            sendBitcoinPayment(amountSat, feesPerKw, mBitcoinInvoice, emptyWallet);
            closeAndGoHome();
          }
        } catch (NumberFormatException e) {
          handlePaymentError(R.string.payment_error_fees_onchain);
        }
      }
    } catch (NumberFormatException e) {
      handlePaymentError(R.string.payment_error_amount);
    } catch (Exception e) {
      Log.w(TAG, "Could not send payment with cause=" + e.getMessage());
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
    mBinding.paymentErrorText.setText(getString(messageId));
    mBinding.paymentError.setVisibility(View.VISIBLE);
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
    final String paymentHash = pr.paymentHash().toString();
    final Payment p = app.getDBHelper().getPayment(paymentHash, PaymentType.BTC_LN);
    if (p != null && p.getStatus() == PaymentStatus.PAID) {
      canNotHandlePayment(R.string.payment_error_paid);
    } else if (p != null && p.getStatus() == PaymentStatus.PENDING) {
      canNotHandlePayment(R.string.payment_error_pending);
    } else {
      AsyncExecutor.create().execute(
        () -> {
          // payment attempt is processed if it does not already exist or is not failed/init
          if (p == null) {
            final String paymentDescription = pr.description().isLeft() ? pr.description().left().get() : pr.description().right().get().toString();
            final Payment newPayment = new Payment();
            newPayment.setType(PaymentType.BTC_LN);
            newPayment.setDirection(PaymentDirection.SENT);
            newPayment.setReference(paymentHash);
            newPayment.setAmountRequestedMsat(WalletUtils.getLongAmountFromInvoice(pr));
            newPayment.setAmountSentMsat(amountMsat);
            newPayment.setRecipient(pr.nodeId().toString());
            newPayment.setPaymentRequest(prAsString.toLowerCase());
            newPayment.setStatus(PaymentStatus.INIT);
            newPayment.setDescription(paymentDescription);
            newPayment.setUpdated(new Date());
            app.getDBHelper().insertOrUpdatePayment(newPayment);
          } else {
            p.setAmountSentMsat(amountMsat);
            p.setUpdated(new Date());
            app.getDBHelper().insertOrUpdatePayment(p);
          }

          // execute payment future, with cltv expiry + 1 to prevent the case where a block is mined just
          // when the payment is made, which would fail the payment.
          Log.i(TAG, "sending " + amountMsat + " msat for invoice " + prAsString);
          app.sendLNPayment(pr, amountMsat, capLightningFees);
        }
      );
      closeAndGoHome();
    }
  }

  /**
   * Sends a Bitcoin transaction.
   *
   * @param amountSat  amount of the tx in satoshis
   * @param feesPerKw  fees to the network in satoshis per kb
   * @param bitcoinURI contains the bitcoin address
   */
  private void sendBitcoinPayment(final Satoshi amountSat, final Long feesPerKw, final BitcoinURI bitcoinURI, final boolean emptyWallet) {
    Log.i(TAG, "sending " + amountSat + " sat invoice " + mBitcoinInvoice.toString());
    if (emptyWallet) {
      Log.i(TAG, "sendBitcoinPayment: emptying wallet with special method....");
      app.sendAllOnchain(bitcoinURI.getAddress(), feesPerKw);
    } else {
      app.sendBitcoinPayment(amountSat, bitcoinURI.getAddress(), feesPerKw);
    }
  }

  /**
   * Handle the visibility and interactivity of form's elements according to the state of the payment.
   * If the payment is being processed (or the PIN dialog is shown) editable inputs and buttons are disabled.
   */
  private void toggleForm() {
    if (isProcessingPayment) {
      mBinding.amountEditableValue.setEnabled(false);
      mBinding.feesValue.setEnabled(false);
      mBinding.feesRating.setEnabled(false);
      mBinding.btnSend.setEnabled(false);
      mBinding.btnCancel.setEnabled(false);
      mBinding.layoutButtons.setAlpha(0.3f);
      mBinding.paymentError.setVisibility(View.GONE);
    } else {
      mBinding.amountEditableValue.setEnabled(true);
      mBinding.feesValue.setEnabled(true);
      mBinding.feesRating.setEnabled(true);
      mBinding.btnSend.setEnabled(true);
      mBinding.btnCancel.setEnabled(true);
      mBinding.layoutButtons.setAlpha(1);
    }
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    toggleForm();
  }
}
