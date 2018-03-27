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

import android.content.Intent;
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.util.AsyncExecutor;

import akka.dispatch.OnComplete;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.channel.Channel;
import fr.acinq.eclair.io.NodeURI;
import fr.acinq.eclair.io.Peer;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityOpenChannelBinding;
import fr.acinq.eclair.wallet.events.LNNewChannelFailureEvent;
import fr.acinq.eclair.wallet.events.LNNewChannelOpenedEvent;
import fr.acinq.eclair.wallet.fragments.PinDialog;
import fr.acinq.eclair.wallet.models.FeeRating;
import fr.acinq.eclair.wallet.tasks.NodeURIReaderTask;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.Validators;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import scala.concurrent.duration.Duration;

public class OpenChannelActivity extends EclairActivity implements NodeURIReaderTask.AsyncNodeURIReaderTaskResponse {

  public static final String EXTRA_NEW_HOST_URI = BuildConfig.APPLICATION_ID + "NEW_HOST_URI";
  private static final String TAG = "OpenChannelActivity";
  final MilliSatoshi minFunding = new MilliSatoshi(100000000); // 1 mBTC
  final MilliSatoshi maxFunding = package$.MODULE$.satoshi2millisatoshi(new Satoshi(Channel.MAX_FUNDING_SATOSHIS()));

  private ActivityOpenChannelBinding mBinding;

  private String remoteNodeURIAsString = "";
  private NodeURI remoteNodeURI = null;
  private String preferredFiatCurrency = Constants.FIAT_USD;
  private CoinUnit preferredBitcoinUnit = CoinUtils.getUnitFromString(Constants.BTC_CODE);
  // state of the fees, used with data binding
  private FeeRating feeRatingState = Constants.FEE_RATING_FAST;
  private PinDialog pinDialog;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_open_channel);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_open_channel);

    final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
    preferredFiatCurrency = WalletUtils.getPreferredFiat(sharedPrefs);
    preferredBitcoinUnit = WalletUtils.getPreferredCoinUnit(sharedPrefs);

    mBinding.capacityValue.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        // toggle hint depending on amount input
        mBinding.capacityHint.setVisibility(s == null || s.length() == 0 ? View.VISIBLE : View.GONE);
        try {
          checkAmount(s.toString());
        } catch (Exception e) {
          Log.d(TAG, "Could not convert amount to number with cause " + e.getMessage());
          mBinding.capacityFiat.setText("");
        }
      }

      @Override
      public void afterTextChanged(Editable s) {
      }
    });
    mBinding.feesValue.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
      }

      @Override
      public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
        try {
          final Long feesSatPerByte = Long.parseLong(s.toString());
          if (feesSatPerByte == app.estimateSlowFees()) {
            mBinding.feesRating.setText(R.string.payment_fees_slow);
          } else if (feesSatPerByte == app.estimateMediumFees()) {
            mBinding.feesRating.setText(R.string.payment_fees_medium);
          } else if (feesSatPerByte == app.estimateFastFees()) {
            mBinding.feesRating.setText(R.string.payment_fees_fast);
          } else {
            mBinding.feesRating.setText(R.string.payment_fees_custom);
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
    mBinding.capacityUnit.setText(preferredBitcoinUnit.shortLabel());
    setFeesToDefault();
    remoteNodeURIAsString = getIntent().getStringExtra(EXTRA_NEW_HOST_URI).trim();
    new NodeURIReaderTask(this, remoteNodeURIAsString).execute();
  }

  @Override
  protected void onResume() {
    super.onResume();
    checkInit();
  }

  public void focusAmount(final View view) {
    mBinding.capacityValue.requestFocus();
  }

  @Override
  protected void onPause() {
    // dismiss the pin dialog if it exists to prevent leak.
    if (pinDialog != null) {
      pinDialog.dismiss();
    }
    super.onPause();
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

  /**
   * Checks if the String amount respects the following rules:
   * <ul>
   * <li>numeric</li>
   * <li>convertible to MilliSatoshi in the current user preferred unit</li>
   * <li>exceeds the minimal capacity amount (1mBTC)</li>
   * <li>does not exceed the maximal capacity amount (167 mBTC)</li>
   * <li>does not exceed the available onchain balance (confirmed + unconfirmed), accounting a minimal required leftover</li>
   * </ul>
   * <p>
   * Show an error in the open channel form if one of the rules is not respected.
   *
   * @param amount string amount
   * @return true if amount is valid, false otherwise
   */
  private boolean checkAmount(final String amount) throws IllegalArgumentException, NullPointerException {
    final MilliSatoshi amountMsat = CoinUtils.convertStringAmountToMsat(amount, preferredBitcoinUnit.code());
    mBinding.capacityFiat.setText(WalletUtils.convertMsatToFiatWithUnit(amountMsat.amount(), preferredFiatCurrency));
    if (amountMsat.amount() < minFunding.amount() || amountMsat.amount() >= maxFunding.amount()) {
      toggleError(getString(R.string.openchannel_capacity_invalid, CoinUtils.formatAmountInUnit(minFunding, preferredBitcoinUnit, false),
        CoinUtils.formatAmountInUnit(maxFunding, preferredBitcoinUnit, true)));
      return false;
    } else if (package$.MODULE$.millisatoshi2satoshi(amountMsat).amount() + Validators.MIN_LEFTOVER_ONCHAIN_BALANCE_SAT > app.onChainBalance.get().amount()) {
      toggleError(getString(R.string.openchannel_capacity_notenoughfunds));
      return false;
    } else {
      mBinding.error.setVisibility(View.GONE);
      return true;
    }
  }

  private void toggleError(final String errorLabel) {
    mBinding.errorValue.setText(errorLabel);
    mBinding.error.setVisibility(View.VISIBLE);
  }

  private void setURIFields(final String pubkey, final String ip, final String port) {
    mBinding.pubkeyValue.setText(pubkey);
    mBinding.ipValue.setText(ip);
    mBinding.portValue.setText(port);
    mBinding.openButton.setVisibility(View.VISIBLE);
  }

  private void disableForm() {
    mBinding.openButton.setEnabled(false);
    mBinding.capacityValue.setEnabled(false);
    mBinding.openButton.setAlpha(0.3f);
  }

  private void enableForm() {
    mBinding.openButton.setEnabled(true);
    mBinding.capacityValue.setEnabled(true);
    mBinding.openButton.setAlpha(1f);
  }

  public void cancelOpenChannel(View view) {
    goToHome();
  }

  private void goToHome() {
    Intent intent = new Intent(getBaseContext(), HomeActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    startActivity(intent);
    finish();
  }

  public void confirmOpenChannel(View view) {
    try {
      if (!checkAmount(mBinding.capacityValue.getText().toString())) {
        return;
      }
    } catch (Exception e) {
      Log.d(TAG, "Could not convert amount to number with cause " + e.getMessage());
      toggleError(getString(R.string.openchannel_error_capacity_nan));
      return;
    }

    try {
      if (Long.parseLong(mBinding.feesValue.getText().toString()) <= 0) {
        toggleError(getString(R.string.openchannel_error_fees_gt_0));
        return;
      }
    } catch (Exception e) {
      Log.w(TAG, "Could not read fees with cause=" + e.getMessage());
      toggleError(getString(R.string.openchannel_error_fees_nan));
      return;
    }

    disableForm();
    if (isPinRequired()) {
      pinDialog = new PinDialog(OpenChannelActivity.this, R.style.CustomAlertDialog, new PinDialog.PinDialogCallback() {
        @Override
        public void onPinConfirm(final PinDialog dialog, final String pinValue) {
          if (isPinCorrect(pinValue, dialog)) {
            doOpenChannel();
          } else {
            toggleError(getString(R.string.payment_error_incorrect_pin));
            enableForm();
          }
        }

        @Override
        public void onPinCancel(PinDialog dialog) {
          enableForm();
        }
      });
      pinDialog.show();
    } else {
      doOpenChannel();
    }
  }

  private void doOpenChannel() {
    final Satoshi fundingSat = CoinUtils.convertStringAmountToSat(mBinding.capacityValue.getText().toString(), preferredBitcoinUnit.code());
    final Long feesPerKw = fr.acinq.eclair.package$.MODULE$.feerateByte2Kw(Long.parseLong(mBinding.feesValue.getText().toString()));
    try {
      Peer.OpenChannel o = new Peer.OpenChannel(remoteNodeURI.nodeId(), fundingSat, new MilliSatoshi(0), scala.Option.apply(feesPerKw), scala.Option.apply(null));
      AsyncExecutor.create().execute(
        () -> {
          OnComplete<Object> onComplete = new OnComplete<Object>() {
            @Override
            public void onComplete(Throwable throwable, Object o) throws Throwable {
              if (throwable != null && throwable instanceof akka.pattern.AskTimeoutException) {
                // future timed out, do not display message
              } else if (throwable != null) {
                EventBus.getDefault().post(new LNNewChannelFailureEvent(throwable.getMessage()));
              } else {
                EventBus.getDefault().post(new LNNewChannelOpenedEvent(remoteNodeURI.nodeId().toString()));
              }
            }
          };
          app.openChannel(Duration.create(30, "seconds"), onComplete, remoteNodeURI, o);
        });
      goToHome();
    } catch (Throwable t) {
      toggleError(t.getLocalizedMessage());
    }
  }

  @Override
  public void processNodeURIFinish(final NodeURI uri, final String errorMessage) {
    this.remoteNodeURI = uri;
    if (this.remoteNodeURI == null || this.remoteNodeURI.address() == null || this.remoteNodeURI.nodeId() == null) {
      final String message = getString(R.string.openchannel_error_address, errorMessage != null ? errorMessage : getString(R.string.openchannel_address_expected_format));
      mBinding.loading.setText(message);
    } else {
      mBinding.loading.setVisibility(View.GONE);
      setURIFields(uri.nodeId().toString(), uri.address().getHost(), String.valueOf(uri.address().getPort()));
      mBinding.form.setVisibility(View.VISIBLE);
      mBinding.capacityValue.requestFocus();
    }
  }
}
