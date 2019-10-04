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

package fr.acinq.eclair.wallet.fragments.openchannel;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.databinding.DataBindingUtil;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.channel.Channel;
import fr.acinq.eclair.io.NodeURI;
import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.activities.OpenChannelActivity;
import fr.acinq.eclair.wallet.databinding.FragmentOpenChannelCapacityBinding;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.TechnicalHelper;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenChannelCapacityFragment extends Fragment {

  private final Logger log = LoggerFactory.getLogger(OpenChannelCapacityFragment.class);
  private NodeURI nodeURI;
  public FragmentOpenChannelCapacityBinding mBinding;

  public void setNodeURI(final NodeURI uri) {
    this.nodeURI = uri;
  }

  OnCapacityConfirmListener mCallback;

  public void setListener(OpenChannelActivity activity) {
    mCallback = activity;
  }

  public interface OnCapacityConfirmListener {
    void onCapacityConfirm(final Satoshi capacity, final long feesSatPerKW, final boolean requireLiquidity);

    void onCapacityBack();
  }

  final Satoshi minFunding = new Satoshi(100000); // 1 mBTC
  final Satoshi maxFunding = Channel.MAX_FUNDING();
  private int feeRatingState = Constants.FEE_RATING_FAST;
  private String preferredFiatCurrency = Constants.FIAT_USD;
  private CoinUnit preferredBitcoinUnit = CoinUtils.getUnitFromString(Constants.BTC_CODE);

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(false);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    mBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_open_channel_capacity, container, false);

    final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
    preferredFiatCurrency = WalletUtils.getPreferredFiat(sharedPrefs);
    preferredBitcoinUnit = WalletUtils.getPreferredCoinUnit(sharedPrefs);
    setFeesToDefault();
    mBinding.setNode(this.nodeURI);

    mBinding.requestLiquidityCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
      mBinding.setGoToLiquidity(isChecked);
    });
    mBinding.capacityLayout.setOnClickListener(v -> focusField(mBinding.capacityValue));
    mBinding.fundingFeesLayout.setOnClickListener(v -> focusField(mBinding.fundingFeesValue));
    mBinding.fundingFeesRating.setOnClickListener(v -> pickFees());
    mBinding.buttonCancel.setOnClickListener(v -> mCallback.onCapacityBack());
    mBinding.buttonNext.setOnClickListener(v -> confirmOpenChannel());
    mBinding.useAllFundsCheckbox.setOnCheckedChangeListener((v, isChecked) -> {
      if (isChecked) {
        useAllAvailableFunds();
      } else {
        mBinding.capacityValue.setEnabled(true);
      }
    });

    mBinding.capacityValue.addTextChangedListener(new TechnicalHelper.SimpleTextWatcher() {
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        // toggle hint depending on amount input
        mBinding.capacityHint.setVisibility(s == null || s.length() == 0 ? View.VISIBLE : View.GONE);
        extractCapacity(s.toString());
      }
    });
    mBinding.fundingFeesValue.addTextChangedListener(new TechnicalHelper.SimpleTextWatcher() {
      @Override
      public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
        extractFundingFees(s.toString());
      }
    });
    mBinding.capacityUnit.setText(preferredBitcoinUnit.shortLabel());
    return mBinding.getRoot();
  }

  /**
   * Reads the fees input by the user and return the value as a sat per kw Long. If the value is invalid, an error
   * message is displayed and this method returns null.
   *
   * @param feesString user input
   * @return null if invalid, Long in satoshi per kw otherwise
   */
  private Long extractFundingFees(final String feesString) {
    mBinding.setFeesWarning(null);
    mBinding.setFeesError(null);
    // fee value changes must invalidate the 'set all available funds' checkbox, since this amount
    // would probably not be correct anymore
    mBinding.useAllFundsCheckbox.setChecked(false);
    try {
      final long feesSatPerByte = Long.parseLong(feesString);
      if (feesSatPerByte != this.getApp().estimateSlowFees() && feesSatPerByte != this.getApp().estimateMediumFees() && feesSatPerByte != this.getApp().estimateFastFees()) {
        feeRatingState = Constants.FEE_RATING_CUSTOM;
        mBinding.setFeeRatingState(feeRatingState);
        mBinding.fundingFeesRating.setText(R.string.payment_fees_custom);
      }
      if (feesSatPerByte <= this.getApp().estimateSlowFees() / 2) {
        mBinding.setFeesWarning(getString(R.string.payment_fees_verylow));
      } else if (feesSatPerByte >= this.getApp().estimateFastFees() * 2) {
        mBinding.setFeesWarning(getString(R.string.payment_fees_veryhigh));
      } else {
        mBinding.setFeesWarning(null);
      }
      return fr.acinq.eclair.package$.MODULE$.feerateByte2Kw(feesSatPerByte);
    } catch (Throwable t) {
      log.debug("could not read fees with cause {}" + t.getMessage());
      mBinding.setFeesWarning(null);
      mBinding.setFeesError(getString(R.string.openchannel_error_fees_invalid));
      return null;
    }
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
   * If one of these rules is not respected, an error message will be added to the display binding and this method will return null.
   *
   * @param amount String amount
   * @return Millisatoshi object if amount is valid, null otherwise
   */
  private Satoshi extractCapacity(final String amount) {
    mBinding.setAmountError(null);
    try {
      final Satoshi capacity = CoinUtils.convertStringAmountToSat(amount, preferredBitcoinUnit.code());
      mBinding.capacityFiat.setText(getString(R.string.amount_to_fiat, WalletUtils.formatSatToFiatWithUnit(capacity, preferredFiatCurrency)));
      if (capacity.$less(minFunding )|| capacity.$greater(maxFunding)) {
        mBinding.setAmountError(getString(R.string.openchannel_capacity_invalid, CoinUtils.formatAmountInUnit(minFunding, preferredBitcoinUnit, false),
          CoinUtils.formatAmountInUnit(maxFunding, preferredBitcoinUnit, true)));
        return null;
      } else if (getApp() != null && capacity.$greater(getApp().getOnchainBalance())) {
        mBinding.setAmountError(getString(R.string.openchannel_capacity_notenoughfunds));
        return null;
      } else {
        mBinding.setAmountError(null);
        return capacity;
      }
    } catch (Throwable t) {
      log.debug("could not convert capacity with cause {}", t.getMessage());
      mBinding.setAmountError(getString(R.string.openchannel_error_capacity_nan));
      return null;
    }
  }

  private void focusField(final View v) {
    v.requestFocus();
    if (getActivity() != null) {
      final InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
      if (imm != null) {
        imm.showSoftInput(v, 0);
      }
    }
  }

  private void useAllAvailableFunds() {
    mBinding.useAllFundsCheckbox.setEnabled(false);
    mBinding.useAllFundsCheckbox.setText(R.string.openchannel_max_funds_pleasewait);
    new Thread() {
      @Override
      public void run() {
        try {
          if (getApp() != null) {
            final Long feesPerKw = fr.acinq.eclair.package$.MODULE$.feerateByte2Kw(Long.parseLong(mBinding.fundingFeesValue.getText().toString()));
            final long capacitySat = Math.min(getApp().getAvailableFundsAfterFees(feesPerKw).toLong(), Channel.MAX_FUNDING().toLong());
            runOnUiThread(() -> {
              mBinding.capacityValue.setText(CoinUtils.rawAmountInUnit(new Satoshi(capacitySat), preferredBitcoinUnit).bigDecimal().toPlainString());
              mBinding.capacityValue.setEnabled(false);
            });
          }
        } catch (Throwable t) {
          log.error("could not retrieve max funds from wallet", t);
        } finally {
          runOnUiThread(() -> {
            mBinding.useAllFundsCheckbox.setEnabled(true);
            mBinding.useAllFundsCheckbox.setText(R.string.openchannel_max_funds);
          });
        }
      }
    }.start();
  }

  private void runOnUiThread(final Runnable action) {
    if (getActivity() != null) {
      getActivity().runOnUiThread(action);
    }
  }

  private App getApp() {
    return (getActivity() != null && getActivity().getApplication() != null) ? (App) getActivity().getApplication() : null;
  }

  private void pickFees() {
    if (getApp() != null) {
      if (feeRatingState == Constants.FEE_RATING_SLOW) {
        feeRatingState = Constants.FEE_RATING_MEDIUM;
        mBinding.fundingFeesValue.setText(String.valueOf(getApp().estimateMediumFees()));
        mBinding.setFeeRatingState(feeRatingState);
        mBinding.fundingFeesRating.setText(R.string.payment_fees_medium);
      } else if (feeRatingState == Constants.FEE_RATING_MEDIUM) {
        feeRatingState = Constants.FEE_RATING_FAST;
        mBinding.fundingFeesValue.setText(String.valueOf(getApp().estimateFastFees()));
        mBinding.setFeeRatingState(feeRatingState);
        mBinding.fundingFeesRating.setText(R.string.payment_fees_fast);
      } else if (feeRatingState == Constants.FEE_RATING_FAST) {
        feeRatingState = Constants.FEE_RATING_SLOW;
        mBinding.fundingFeesValue.setText(String.valueOf(getApp().estimateSlowFees()));
        mBinding.setFeeRatingState(feeRatingState);
        mBinding.fundingFeesRating.setText(R.string.payment_fees_slow);
      } else {
        setFeesToDefault();
      }
    }
  }

  private void setFeesToDefault() {
    feeRatingState = Constants.FEE_RATING_FAST;
    mBinding.fundingFeesValue.setText(String.valueOf(getApp().estimateFastFees()));
    mBinding.setFeeRatingState(feeRatingState);
    mBinding.fundingFeesRating.setText(R.string.payment_fees_fast);
  }

  private void confirmOpenChannel() {
    if (mCallback != null) {
      final Satoshi capacity = extractCapacity(mBinding.capacityValue.getText().toString());
      final Long feesSatPerKW = extractFundingFees(mBinding.fundingFeesValue.getText().toString());
      if (capacity != null && feesSatPerKW != null) {
        // amount and fees are correct, notify parent activity to move to next step
        mCallback.onCapacityConfirm(capacity, feesSatPerKW, mBinding.requestLiquidityCheckbox.isChecked());
      }
    }
  }
}

