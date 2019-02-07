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

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import fr.acinq.bitcoin.MilliBtc;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.eclair.io.NodeURI;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.activities.OpenChannelActivity;
import fr.acinq.eclair.wallet.databinding.FragmentOpenChannelLiquidityBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import fr.acinq.bitcoin.package$;
import scala.math.BigDecimal;

public class OpenChannelLiquidityFragment extends Fragment {

  private final Logger log = LoggerFactory.getLogger(OpenChannelLiquidityFragment.class);
  public FragmentOpenChannelLiquidityBinding mBinding;

  private Satoshi capacity = null;
  private Long feesSatPerKW = null;

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

    mBinding.liquidityOptNone.setOnClickListener(v -> mBinding.setLiquidityOpt(0));
    mBinding.liquidityOpt5.setOnClickListener(v -> mBinding.setLiquidityOpt(1));
    mBinding.liquidityOpt10.setOnClickListener(v -> mBinding.setLiquidityOpt(2));

    mBinding.buttonBack.setOnClickListener(v -> {
      mCallback.onLiquidityBack();
    });
    mBinding.buttonNext.setOnClickListener(v -> {
      MilliBtc push;
      switch (mBinding.getLiquidityOpt()) {
        case 1: {
          push = new MilliBtc(BigDecimal.exact(0.05));
          break;
        }
        case 2: {
          push = new MilliBtc(BigDecimal.exact(0.1));
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

