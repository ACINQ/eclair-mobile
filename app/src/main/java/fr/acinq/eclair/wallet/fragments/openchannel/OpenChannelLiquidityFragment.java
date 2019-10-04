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
import fr.acinq.bitcoin.MilliBtc;
import fr.acinq.eclair.MilliSatoshi;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.wallet.App;
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

  private enum LIQUIDITY_REQUESTS {
    _10_MBTC(10),
    _25_MBTC(25),
    _50_MBTC(50);
    private final Logger log = LoggerFactory.getLogger(LIQUIDITY_REQUESTS.class);
    private final MilliBtc inboundCapacity;
    private final MilliSatoshi cost;

    LIQUIDITY_REQUESTS(final long inboundCapacity_mbtc) {
      this.inboundCapacity = new MilliBtc(BigDecimal.exact(inboundCapacity_mbtc));
      if (App.walletContext != null) {
        this.cost = MilliSatoshi.toMilliSatoshi(inboundCapacity.$times(App.walletContext.liquidityRate));
      } else {
        this.cost = new MilliSatoshi(0);
      }
    }
  }

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
      CoinUtils.formatAmountInUnit(LIQUIDITY_REQUESTS._10_MBTC.inboundCapacity, preferredBitcoinUnit, true)));
    mBinding.liquidityOpt10Cost.setText(getString(R.string.openchannel_liquidity_cost,
      CoinUtils.formatAmountInUnit(LIQUIDITY_REQUESTS._10_MBTC.cost, preferredBitcoinUnit, true),
      WalletUtils.formatMsatToFiatWithUnit(LIQUIDITY_REQUESTS._10_MBTC.cost, fiatUnit)));

    mBinding.liquidityOpt25.setOnClickListener(v -> mBinding.setLiquidityOpt(2));
    mBinding.liquidityOpt25Title.setText(getString(R.string.openchannel_liquidity_label,
      CoinUtils.formatAmountInUnit(LIQUIDITY_REQUESTS._25_MBTC.inboundCapacity, preferredBitcoinUnit, true)));
    mBinding.liquidityOpt25Cost.setText(getString(R.string.openchannel_liquidity_cost,
      CoinUtils.formatAmountInUnit(LIQUIDITY_REQUESTS._25_MBTC.cost, preferredBitcoinUnit, true),
      WalletUtils.formatMsatToFiatWithUnit(LIQUIDITY_REQUESTS._25_MBTC.cost, fiatUnit)));

    mBinding.liquidityOpt50.setOnClickListener(v -> mBinding.setLiquidityOpt(3));
    mBinding.liquidityOpt50Title.setText(getString(R.string.openchannel_liquidity_label,
      CoinUtils.formatAmountInUnit(LIQUIDITY_REQUESTS._50_MBTC.inboundCapacity, preferredBitcoinUnit, true)));
    mBinding.liquidityOpt50Cost.setText(getString(R.string.openchannel_liquidity_cost,
      CoinUtils.formatAmountInUnit(LIQUIDITY_REQUESTS._50_MBTC.cost, preferredBitcoinUnit, true),
      WalletUtils.formatMsatToFiatWithUnit(LIQUIDITY_REQUESTS._50_MBTC.cost, fiatUnit)));

    mBinding.buttonBack.setOnClickListener(v -> {
      mBinding.setLiquidityOpt(0);
      mCallback.onLiquidityBack();
    });

    mBinding.buttonNext.setOnClickListener(v -> {
      MilliSatoshi push = new MilliSatoshi(0);
      switch (mBinding.getLiquidityOpt()) {
        case 1: {
          push = LIQUIDITY_REQUESTS._10_MBTC.cost;
          break;
        }
        case 2: {
          push = LIQUIDITY_REQUESTS._25_MBTC.cost;
          break;
        }
        case 3: {
          push = LIQUIDITY_REQUESTS._50_MBTC.cost;
          break;
        }
      }
      mCallback.onLiquidityConfirm(this.capacity, this.feesSatPerKW, push);
    });

    return mBinding.getRoot();
  }

}

