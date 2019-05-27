/*
 * Copyright 2019 ACINQ SAS
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
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import com.google.common.base.Strings;
import fr.acinq.bitcoin.ByteVector32;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.Globals;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.actors.NodeSupervisor;
import fr.acinq.eclair.wallet.databinding.ActivitySendPaymentBinding;
import fr.acinq.eclair.wallet.fragments.PinDialog;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDirection;
import fr.acinq.eclair.wallet.models.PaymentStatus;
import fr.acinq.eclair.wallet.models.PaymentType;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.TechnicalHelper;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import fr.acinq.eclair.wallet.models.BitcoinURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class SendPaymentActivity extends EclairActivity {

  private final Logger log = LoggerFactory.getLogger(SendPaymentActivity.class);

  public static final String EXTRA_INVOICE = BuildConfig.APPLICATION_ID + ".EXTRA_INVOICE";
  private final static List<String> LIGHTNING_SCHEMES = Arrays.asList("lightning://", "lightning:");
  final String chain = Character.toUpperCase(BuildConfig.CHAIN.charAt(0)) +  BuildConfig.CHAIN.substring(1);

  public enum Steps {
    LOADING,
    INVOICE_READING_ERROR,
    PICK_PAYMENT_SETTLEMENT_TYPE,
    ON_CHAIN_PAYMENT,
    LIGHTNING_PAYMENT,
  }

  private boolean isProcessingPayment = false;
  private Either<BitcoinURI, PaymentRequest> invoice;
  private String invoiceAsString = null;

  private ActivitySendPaymentBinding mBinding;

  private CoinUnit preferredBitcoinUnit = CoinUtils.getUnitFromString("btc");
  private String preferredFiatCurrency = Constants.FIAT_USD;
  // state of the fees, used with data binding
  private int feeRatingState = Constants.FEE_RATING_FAST;
  private boolean capLightningFees = true;
  private PinDialog pinDialog;

  /**
   * Checks if a payment request is correct; if not, display a message.
   *
   * @return true if the payment can be handled, false otherwise
   */
  private boolean checkPaymentRequestValid(final PaymentRequest paymentRequest) {
    // check payment request chain
    final Option<String> acceptedPrefix = PaymentRequest.prefixes().get(WalletUtils.getChainHash());
    if (acceptedPrefix.isEmpty() || !acceptedPrefix.get().equals(paymentRequest.prefix())) {
      canNotHandlePayment(getString(R.string.payment_ln_invalid_chain, chain));
      return false;
    }
    // check that payment is not already processed
    final Payment paymentInDB = app.getDBHelper().getPayment(paymentRequest.paymentHash().toString(), PaymentType.BTC_LN);
    if (paymentInDB != null && paymentInDB.getStatus() == PaymentStatus.PENDING) {
      canNotHandlePayment(R.string.payment_error_pending);
      return false;
    } else if (paymentInDB != null && paymentInDB.getStatus() == PaymentStatus.PAID) {
      canNotHandlePayment(R.string.payment_error_paid);
      return false;
    }
    // check that the payment has not expired
    long expirySecs = paymentRequest.expiry().isDefined() ? (Long) paymentRequest.expiry().get() : 60 * 60; // default expiry is 1 hour = 3600 seconds
    if (paymentRequest.timestamp() + expirySecs < System.currentTimeMillis() / 1000) {
      canNotHandlePayment(R.string.payment_ln_expiry_outdated);
      return false;
    }
    // check lightning channels status
    if (NodeSupervisor.getChannelsMap().size() == 0) {
      canNotHandlePayment(R.string.payment_error_ln_no_channels);
      return false;
    }
    // check channels balance is sufficient
    if (paymentRequest.amount().isDefined() && !NodeSupervisor.hasNormalChannelsWithBalance(WalletUtils.getAmountFromInvoice(paymentRequest).amount())) {
      canNotHandlePayment(getString(R.string.payment_error_ln_insufficient_funds));
      return false;
    }
    return true;
  }

  /**
   * Checks if the wallet is ready to send a payment.
   * <p>
   * - Wallet's block height must be reasonably close to the expected min height.
   * - Wallet must be connected to an electrum server
   * <p>
   * Furthermore, if this is a LN payment, at least 1 lightning channel must be active.
   *
   * @return true if wallet is ready, false otherwise
   */
  private boolean checkWalletReady() {
    // check that wallet is not desync, or very late compared to chain
    final boolean isBlockHeightCorrect = Globals.blockCount().get() > Constants.MIN_BLOCK_HEIGHT;
    // if this is a LN payment, send button is enabled only if there is at least 1 channel capable of processing the payment.
    final boolean isWalletReady = app.getElectrumState().isConnected && isBlockHeightCorrect && (!isLightningInvoice() || NodeSupervisor.hasOneNormalChannel());
    mBinding.setEnableSendButton(isWalletReady);
    new Handler().postDelayed(this::checkWalletReady, 1000);
    return isWalletReady;
  }

  private void setupOnchainPaymentForm(final BitcoinURI bitcoinURI) {
    checkWalletReady();
    if (bitcoinURI.amount != null) {
      final MilliSatoshi amountMsat = package$.MODULE$.satoshi2millisatoshi(bitcoinURI.amount);
      mBinding.amountEditableHint.setVisibility(View.GONE);
      mBinding.amountEditableValue.setText(CoinUtils.rawAmountInUnit(amountMsat, preferredBitcoinUnit).bigDecimal().toPlainString());
      mBinding.amountFiat.setText(WalletUtils.formatMsatToFiatWithUnit(amountMsat.amount(), preferredFiatCurrency));
    } else {
      // only open the keyboard forcibly if no amount was set in the URI. This makes for a cleaner initial display.
      forceFocusAmount(null);
    }
    setFeesToDefault();
    mBinding.recipientValue.setText(bitcoinURI.address);
    mBinding.setPaymentStep(Steps.ON_CHAIN_PAYMENT);
    invoice = Left.apply(bitcoinURI);
  }

  private void setupLightningPaymentForm(final PaymentRequest paymentRequest) {
    if (checkPaymentRequestValid(paymentRequest)) {
      checkWalletReady();
      if (!capLightningFees) {
        mBinding.feesWarning.setText(R.string.payment_fees_not_capped);
        mBinding.feesWarning.setVisibility(View.VISIBLE);
      }
      if (paymentRequest.amount().isDefined()) {
        final MilliSatoshi amountMsat = WalletUtils.getAmountFromInvoice(paymentRequest);
        mBinding.amountEditableValue.setText(CoinUtils.rawAmountInUnit(amountMsat, preferredBitcoinUnit).bigDecimal().toPlainString());
        mBinding.amountFiat.setText(WalletUtils.formatMsatToFiatWithUnit(amountMsat.amount(), preferredFiatCurrency));
      }
      mBinding.recipientValue.setText(paymentRequest.nodeId().toString());
      final Either<String, ByteVector32> desc = paymentRequest.description();
      mBinding.descriptionValue.setText(desc.isLeft() ? desc.left().get() : desc.right().get().toString());
      if (paymentRequest.amount().isEmpty()) {
        // only open the keyboard forcibly if no amount was set in the payment request. This makes for a cleaner initial
        // display in the regular cases (that is, payment request has a predefined amount).
        forceFocusAmount(null);
      }
      mBinding.setPaymentStep(Steps.LIGHTNING_PAYMENT);
      invoice = Right.apply(paymentRequest);
    }
  }

  private void canNotHandlePayment(final int messageId) {
    canNotHandlePayment(getString(messageId));
  }

  @UiThread
  private void canNotHandlePayment(final String message) {
    mBinding.readError.setText(message);
    mBinding.setPaymentStep(Steps.INVOICE_READING_ERROR);
  }

  /**
   * Displays the various fields in the payment form, depending on the payment type.
   */
  private void invoiceReadSuccessfully(final Either<BitcoinURI, PaymentRequest> pInvoice) {
    if (pInvoice != null && pInvoice.isLeft() && pInvoice.left().get() != null) {
      final BitcoinURI bitcoinURI = pInvoice.left().get();
      // bitcoin uri with an embedded lightning invoice => user must choose
      if (bitcoinURI.lightning != null) {
        if (NodeSupervisor.getChannelsMap().isEmpty()) {
          mBinding.pickLightningError.setText(R.string.payment_error_ln_pick_no_channels);
          mBinding.pickLightningError.setVisibility(View.VISIBLE);
          mBinding.pickLightningImage.setAlpha(0.3f);
          mBinding.pickLightning.setEnabled(false);
        } else {
          mBinding.pickLightning.setOnClickListener(v -> setupLightningPaymentForm(bitcoinURI.lightning));
        }
        mBinding.pickOnchain.setOnClickListener(v -> setupOnchainPaymentForm(bitcoinURI));
        mBinding.setPaymentStep(Steps.PICK_PAYMENT_SETTLEMENT_TYPE);
      } else {
        setupOnchainPaymentForm(bitcoinURI);
      }
    } else if (pInvoice != null && pInvoice.isRight() && pInvoice.right().get() != null) {
      setupLightningPaymentForm(pInvoice.right().get());
    } else {
      closeAndGoHome();
    }
  }

  public void forceFocusAmount(final View view) {
    mBinding.amountEditableValue.requestFocus();
    final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.showSoftInput(mBinding.amountEditableValue, 0);
    }
  }

  public void forceFocusFees(final View view) {
    mBinding.feesValue.requestFocus();
    final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.showSoftInput(mBinding.feesValue, 0);
    }
  }

  public void pickFees(final View view) {
    if (feeRatingState == Constants.FEE_RATING_SLOW) {
      feeRatingState = Constants.FEE_RATING_MEDIUM;
      mBinding.feesValue.setText(String.valueOf(App.estimateMediumFees()));
      mBinding.setFeeRatingState(feeRatingState);
      mBinding.feesRating.setText(R.string.payment_fees_medium);
    } else if (feeRatingState == Constants.FEE_RATING_MEDIUM) {
      feeRatingState = Constants.FEE_RATING_FAST;
      mBinding.feesValue.setText(String.valueOf(App.estimateFastFees()));
      mBinding.setFeeRatingState(feeRatingState);
      mBinding.feesRating.setText(R.string.payment_fees_fast);
    } else if (feeRatingState == Constants.FEE_RATING_FAST) {
      feeRatingState = Constants.FEE_RATING_SLOW;
      mBinding.feesValue.setText(String.valueOf(App.estimateSlowFees()));
      mBinding.setFeeRatingState(feeRatingState);
      mBinding.feesRating.setText(R.string.payment_fees_slow);
    } else {
      setFeesToDefault();
    }
  }

  private void setFeesToDefault() {
    feeRatingState = Constants.FEE_RATING_FAST;
    mBinding.feesValue.setText(String.valueOf(App.estimateFastFees()));
    mBinding.setFeeRatingState(feeRatingState);
    mBinding.feesRating.setText(R.string.payment_fees_fast);
  }

  /**
   * Prepare the execution of the current payment request stored in the activity, be it an on-chain payment or a lightning payment.
   * Opens a PIN dialog to confirm the payment. If the PIN is correct the payment is executed.
   */
  public void confirmPayment(final View view) {

    if (!checkWalletReady()) {
      mBinding.setEnableSendButton(false);
      return;
    }

    // Stop if a payment is already being processed
    if (isProcessingPayment) return;

    // Update visuals
    isProcessingPayment = true;
    toggleForm();

    // Get amount and executes payment. Depending on the settings, the user must first enter the correct PIN code
    try {
      if (isLightningInvoice()) {
        final PaymentRequest paymentRequest = invoice.right().get();
        final long amountMsat = CoinUtils.convertStringAmountToMsat(mBinding.amountEditableValue.getText().toString(), preferredBitcoinUnit.code()).amount();
        if (isPinRequired()) {
          pinDialog = new PinDialog(SendPaymentActivity.this, R.style.FullScreenDialog, new PinDialog.PinDialogCallback() {
            @Override
            public void onPinConfirm(final PinDialog dialog, final String pinValue) {
              if (isPinCorrect(pinValue, dialog)) {
                sendLNPayment(amountMsat, paymentRequest, invoiceAsString);
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
          sendLNPayment(amountMsat, paymentRequest, invoiceAsString);
          closeAndGoHome();
        }
      } else if (isOnchainInvoice()) {
        final BitcoinURI bitcoinURI = invoice.left().get();
        final Satoshi amountSat = CoinUtils.convertStringAmountToSat(mBinding.amountEditableValue.getText().toString(), preferredBitcoinUnit.code());
        if (amountSat.$greater(app.getOnchainBalance())) {
          handlePaymentError(R.string.payment_error_amount_onchain_insufficient_funds);
          return;
        }
        try {
          final Long feesPerKw = fr.acinq.eclair.package$.MODULE$.feerateByte2Kw(Long.parseLong(mBinding.feesValue.getText().toString()));
          final boolean emptyWallet = mBinding.emptyOnchainWallet.isChecked();
          if (isPinRequired()) {
            pinDialog = new PinDialog(SendPaymentActivity.this, R.style.FullScreenDialog, new PinDialog.PinDialogCallback() {
              public void onPinConfirm(final PinDialog dialog, final String pinValue) {
                if (isPinCorrect(pinValue, dialog)) {
                  sendBitcoinPayment(amountSat, feesPerKw, bitcoinURI, emptyWallet);
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
            sendBitcoinPayment(amountSat, feesPerKw, bitcoinURI, emptyWallet);
            closeAndGoHome();
          }
        } catch (NumberFormatException e) {
          handlePaymentError(R.string.payment_error_fees_onchain);
        }
      }
    } catch (NumberFormatException e) {
      handlePaymentError(R.string.payment_error_amount);
    } catch (Exception e) {
      log.error("could not send payment with cause {}", e.getMessage());
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
    mBinding.paymentError.setText(getString(messageId));
    mBinding.paymentError.setVisibility(View.VISIBLE);
  }

  /**
   * Executes a Lightning payment in an asynchronous task.
   *
   * @param amountMsat amount of the payment in milli satoshis
   * @param pr         lightning payment request
   * @param prAsString payment request as a string (used for display)
   */
  private void sendLNPayment(final long amountMsat, final PaymentRequest pr, final String prAsString) {
    new Thread() {
      @Override
      public void run() {
        final String paymentHash = pr.paymentHash().toString();
        final Payment p = app.getDBHelper().getPayment(paymentHash, PaymentType.BTC_LN);

        // 1 - check if payment exists in DB
        if (p != null && (p.getStatus() == PaymentStatus.INIT || p.getStatus() == PaymentStatus.PENDING)) {
          // Payment is already initializing (~ computing route) or pending (~ en route in netwok), abort payment.
          canNotHandlePayment(R.string.payment_error_pending);
          return;
        } else if (p != null && p.getStatus() == PaymentStatus.PAID) {
          // Payment already paid, abort payment.
          canNotHandlePayment(R.string.payment_error_paid);
          return;
        } else if (p != null && p.getStatus() == PaymentStatus.FAILED) {
          // Payment exists but has failed, retry it with new amount.
          p.setAmountSentMsat(amountMsat);
          p.setUpdated(new Date());
          p.setStatus(PaymentStatus.INIT);
          app.getDBHelper().insertOrUpdatePayment(p);
        } else if (p == null) {
          // Payment does not exist yet, insert it in DB with init state.
          // Note that init payment are cleared when starting the app, so any payment stuck in limbo would be erased.
          // Pending payment (aka en route in network) are guaranteed to resolve to failure or success.
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
          log.warn("unable to handle state of payment {}", p);
          canNotHandlePayment(R.string.payment_error_unhandled_state);
          return;
        }

        // 2 - send payment to the payment initiator
        app.sendLNPayment(pr, amountMsat, capLightningFees);
        runOnUiThread(() -> closeAndGoHome());
      }
    }.start();
  }

  /**
   * Sends a Bitcoin transaction.
   *
   * @param amountSat  amount of the tx in satoshis
   * @param feesPerKw  fees to the network in satoshis per kb
   * @param bitcoinURI contains the bitcoin address
   */
  private void sendBitcoinPayment(final Satoshi amountSat, final Long feesPerKw, final BitcoinURI bitcoinURI, final boolean emptyWallet) {
    if (emptyWallet) {
      log.info("(on-chain) emptying wallet for {} msat, destination={}", amountSat, bitcoinURI.toString());
      app.sendAllOnchain(bitcoinURI.address, feesPerKw);
    } else {
      log.info("(on-chain) sending {} msat for uri {}", amountSat, bitcoinURI.toString());
      app.sendOnchain(amountSat, bitcoinURI.address, feesPerKw);
    }
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
      mBinding.btnSend.setAlpha(0.3f);
      mBinding.paymentError.setVisibility(View.GONE);
    } else {
      mBinding.amountEditableValue.setEnabled(true);
      mBinding.feesValue.setEnabled(true);
      mBinding.feesRating.setEnabled(true);
      mBinding.btnSend.setEnabled(true);
      mBinding.btnCancel.setEnabled(true);
      mBinding.btnSend.setAlpha(1);
    }
  }

  private boolean isOnchainInvoice() {
    return invoice != null && invoice.isLeft() && invoice.left().get() != null;
  }

  private boolean isLightningInvoice() {
    return invoice != null && invoice.isRight() && invoice.right().get() != null;
  }

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_send_payment);
    setFeesToDefault();
    mBinding.setPaymentStep(Steps.LOADING);
    mBinding.setEnableSendButton(true);
    final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
    preferredBitcoinUnit = WalletUtils.getPreferredCoinUnit(sharedPref);
    preferredFiatCurrency = WalletUtils.getPreferredFiat(sharedPref);
    capLightningFees = sharedPref.getBoolean(Constants.SETTING_CAP_LIGHTNING_FEES, true);
    mBinding.amountEditableUnit.setText(preferredBitcoinUnit.shortLabel());

    mBinding.amountEditableValue.addTextChangedListener(new TechnicalHelper.SimpleTextWatcher() {
      @SuppressLint("SetTextI18n")
      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        // toggle hint depending on amount input
        mBinding.amountEditableHint.setVisibility(s == null || s.length() == 0 ? View.VISIBLE : View.GONE);
        try {
          final MilliSatoshi amountMsat = CoinUtils.convertStringAmountToMsat(s.toString(), preferredBitcoinUnit.code());
          mBinding.amountFiat.setText(WalletUtils.formatMsatToFiatWithUnit(amountMsat.amount(), preferredFiatCurrency));
          if (invoice != null && invoice.isLeft()) {
            if (package$.MODULE$.millisatoshi2satoshi(amountMsat).$greater(app.getOnchainBalance())) {
              handlePaymentError(R.string.payment_error_amount_onchain_insufficient_funds);
            } else {
              mBinding.paymentError.setVisibility(View.GONE);
            }
          }
        } catch (Exception e) {
          log.debug("could not read amount with cause {}", e.getMessage());
          mBinding.amountFiat.setText("0 " + preferredFiatCurrency.toUpperCase());
        }
      }
    });

    mBinding.feesValue.addTextChangedListener(new TechnicalHelper.SimpleTextWatcher() {
      @SuppressLint("SetTextI18n")
      @Override
      public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
        try {
          final long feesSatPerByte = Long.parseLong(s.toString());
          if (feesSatPerByte != App.estimateSlowFees() && feesSatPerByte != App.estimateMediumFees() && feesSatPerByte != App.estimateFastFees()) {
            feeRatingState = Constants.FEE_RATING_CUSTOM;
            mBinding.setFeeRatingState(feeRatingState);
            mBinding.feesRating.setText(R.string.payment_fees_custom);
          }
          if (feesSatPerByte <= App.estimateSlowFees() / 2) {
            mBinding.feesWarning.setText(R.string.payment_fees_verylow);
            mBinding.feesWarning.setVisibility(View.VISIBLE);
          } else if (feesSatPerByte >= App.estimateFastFees() * 2) {
            mBinding.feesWarning.setText(R.string.payment_fees_veryhigh);
            mBinding.feesWarning.setVisibility(View.VISIBLE);
          } else {
            mBinding.feesWarning.setVisibility(View.GONE);
          }
        } catch (NumberFormatException e) {
          log.debug("could not read fees with cause {}", e.getMessage());
        }
      }
    });

    mBinding.emptyOnchainWallet.setOnCheckedChangeListener((buttonView, isChecked) -> {
      if (invoice != null && invoice.isLeft()) {
        if (isChecked) {
          mBinding.emptyWalletDisclaimer.setVisibility(View.VISIBLE);
          mBinding.amountEditableValue.setEnabled(false);
          mBinding.amountEditableValue.setText(CoinUtils.rawAmountInUnit(app.getOnchainBalance(), preferredBitcoinUnit).bigDecimal().toPlainString());
        } else {
          mBinding.emptyWalletDisclaimer.setVisibility(View.GONE);
          mBinding.amountEditableValue.setEnabled(true);
        }
      }
    });
  }

  @Override
  protected void onStart() {
    super.onStart();
    new Thread() {
      @Override
      public void run() {
        readInvoiceIntent();
      }
    }.start();
  }

  @WorkerThread
  private void readInvoiceIntent() {
    final Intent intent = getIntent();
    invoiceAsString = intent.getStringExtra(EXTRA_INVOICE).replaceAll("\\u00A0", "").trim();
    log.info("initializing payment with invoice={}", invoiceAsString);

    if (Strings.isNullOrEmpty(invoiceAsString)) {
      runOnUiThread(() -> canNotHandlePayment(getString(R.string.payment_invalid_generic, chain)));
      return;
    }

    try {
      // -- try to read it as a lightning PR
      final PaymentRequest request = PaymentRequest.read(stripLightningScheme(invoiceAsString));
      runOnUiThread(() -> invoiceReadSuccessfully(Right.apply(request)));
    } catch (Throwable lightningThrow) {
      if (invoiceAsString.matches("(lightning:)(//){0,1}.*")) {
        // invoice was intended as a lightning payment
        log.debug("could not read lightning invoice {}", invoiceAsString, lightningThrow);
        runOnUiThread(() -> canNotHandlePayment(getString(R.string.payment_invalid_lightning, chain)));
      } else {
        try {
          // -- try to read invoice as an onchain URI
          final BitcoinURI uri = new BitcoinURI(invoiceAsString);
          runOnUiThread(() -> invoiceReadSuccessfully(Left.apply(uri)));
        } catch (Throwable onchainThrow) {
          if (invoiceAsString.matches("(bitcoin:)(//){0,1}.*")) {
            // invoice was intended as an onchain payment
            log.debug("could not read on-chain uri {}", invoiceAsString, onchainThrow);
            runOnUiThread(() -> canNotHandlePayment(getString(R.string.payment_invalid_onchain, chain)));
          } else {
            // -- cannot read and no info about recipient intention, print generic message
            log.debug("could not read this invoice either as an onchain or a lightning payment{}", invoiceAsString);
            runOnUiThread(() -> canNotHandlePayment(getString(R.string.payment_invalid_generic, chain)));
          }
        }
      }
    }
  }

  private String stripLightningScheme(final String invoice) {
    for (final String scheme : LIGHTNING_SCHEMES) {
      if (invoice.toLowerCase().startsWith(scheme)) {
        return invoice.substring(scheme.length());
      }
    }
    return invoice;
  }

  @Override
  protected void onResume() {
    super.onResume();
    checkInit();
  }

  @Override
  protected void onPause() {
    // dismiss the pin dialog if it exists to prevent leak.
    if (pinDialog != null) {
      pinDialog.dismiss();
    }
    super.onPause();
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    toggleForm();
  }
}
