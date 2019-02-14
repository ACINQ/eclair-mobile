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

package fr.acinq.eclair.wallet.fragments.openchannel;

import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import fr.acinq.bitcoin.MilliBtc;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.activities.OpenChannelActivity;
import fr.acinq.eclair.wallet.databinding.FragmentOpenChannelLiquidityBinding;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.math.BigDecimal;

public class OpenChannelLiquidityFragment extends Fragment {

  private final Logger log = LoggerFactory.getLogger(OpenChannelLiquidityFragment.class);
  public FragmentOpenChannelLiquidityBinding mBinding;

  private Satoshi capacity = null;
  private Long feesSatPerKW = null;
  private static MilliBtc liquidity1 = new MilliBtc(BigDecimal.exact(10));
  private static MilliBtc pushFor1 = new MilliBtc(BigDecimal.exact(0.1));
  private static MilliBtc liquidity2 = new MilliBtc(BigDecimal.exact(25));
  private static MilliBtc pushFor2 = new MilliBtc(BigDecimal.exact(0.25));
  private static MilliBtc liquidity3 = new MilliBtc(BigDecimal.exact(50));
  private static MilliBtc pushFor3 = new MilliBtc(BigDecimal.exact(0.5));

  public void setCapacityAndFees(final Satoshi capacity, final Long feesSatPerKW) {
    this.capacity = capacity;
    this.feesSatPerKW = feesSatPerKW;
  }

  OnLiquidityConfirmListener mCallback;

  public interface OnLiquidityConfirmListener {
    void onLiquidityConfirm(final Satoshi capacity, final Long feesSatPerByte, final MilliSatoshi push);

    void onLiquidityBack();
  }

  public void setListener(OpenChannelActivity activity) {
    mCallback = activity;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(false);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    mBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_open_channel_liquidity, container, false);

    final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
    final CoinUnit preferredBitcoinUnit = WalletUtils.getPreferredCoinUnit(sharedPrefs);
    final String fiatUnit = WalletUtils.getPreferredFiat(sharedPrefs);

    mBinding.liquidityOpt10.setOnClickListener(v -> mBinding.setLiquidityOpt(1));
    mBinding.liquidityOpt10Title.setText(getString(R.string.openchannel_liquidity_label,
      CoinUtils.formatAmountInUnit(liquidity1, preferredBitcoinUnit, true)));
    mBinding.liquidityOpt10Cost.setText(getString(R.string.openchannel_liquidity_cost,
      CoinUtils.formatAmountInUnit(pushFor1, preferredBitcoinUnit, true),
      WalletUtils.convertMsatToFiat(package$.MODULE$.millibtc2millisatoshi(pushFor1).amount(), fiatUnit)));

    mBinding.liquidityOpt25.setOnClickListener(v -> mBinding.setLiquidityOpt(2));
    mBinding.liquidityOpt25Title.setText(getString(R.string.openchannel_liquidity_label,
      CoinUtils.formatAmountInUnit(liquidity2, preferredBitcoinUnit, true)));
    mBinding.liquidityOpt25Cost.setText(getString(R.string.openchannel_liquidity_cost,
      CoinUtils.formatAmountInUnit(pushFor2, preferredBitcoinUnit, true),
      WalletUtils.convertMsatToFiat(package$.MODULE$.millibtc2millisatoshi(pushFor2).amount(), fiatUnit)));

    mBinding.liquidityOpt50.setOnClickListener(v -> mBinding.setLiquidityOpt(3));
    mBinding.liquidityOpt50Title.setText(getString(R.string.openchannel_liquidity_label,
      CoinUtils.formatAmountInUnit(liquidity3, preferredBitcoinUnit, true)));
    mBinding.liquidityOpt50Cost.setText(getString(R.string.openchannel_liquidity_cost,
      CoinUtils.formatAmountInUnit(pushFor3, preferredBitcoinUnit, true),
      WalletUtils.convertMsatToFiat(package$.MODULE$.millibtc2millisatoshi(pushFor3).amount(), fiatUnit)));

    mBinding.buttonBack.setOnClickListener(v -> {
      mBinding.setLiquidityOpt(0);
      mCallback.onLiquidityBack();
    });

    mBinding.buttonNext.setOnClickListener(v -> {
      MilliBtc push;
      switch (mBinding.getLiquidityOpt()) {
        case 1: {
          push = pushFor1;
          break;
        }
        case 2: {
          push = pushFor2;
          break;
        }
        case 3: {
          push = pushFor2;
          break;
        }
        default: {
          push = new MilliBtc(BigDecimal.exact(0));
        }
      }
      mCallback.onLiquidityConfirm(this.capacity, this.feesSatPerKW, package$.MODULE$.millibtc2millisatoshi(push));
    });

    return mBinding.getRoot();
  }

}

