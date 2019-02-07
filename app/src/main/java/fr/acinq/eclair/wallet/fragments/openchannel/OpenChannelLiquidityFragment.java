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
  private static MilliBtc pushFor10mbtc = new MilliBtc(BigDecimal.exact(0.1));
  private static MilliBtc pushFor20mbtc = new MilliBtc(BigDecimal.exact(0.2));

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

    mBinding.liquidityOptNone.setOnClickListener(v -> mBinding.setLiquidityOpt(0));
    mBinding.liquidityOpt10.setOnClickListener(v -> mBinding.setLiquidityOpt(1));
    mBinding.liquidityOpt10Cost.setText(getString(R.string.openchannel_liquidity_cost, CoinUtils.formatAmountInUnit(pushFor10mbtc, preferredBitcoinUnit, true)));
    mBinding.liquidityOpt20.setOnClickListener(v -> mBinding.setLiquidityOpt(2));
    mBinding.liquidityOpt20Cost.setText(getString(R.string.openchannel_liquidity_cost, CoinUtils.formatAmountInUnit(pushFor20mbtc, preferredBitcoinUnit, true)));

    mBinding.buttonBack.setOnClickListener(v -> {
      mCallback.onLiquidityBack();
    });

    mBinding.buttonNext.setOnClickListener(v -> {
      MilliBtc push;
      switch (mBinding.getLiquidityOpt()) {
        case 1: {
          push = pushFor10mbtc;
          break;
        }
        case 2: {
          push = pushFor20mbtc;
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

