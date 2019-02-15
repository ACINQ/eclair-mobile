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

package fr.acinq.eclair.wallet.fragments;

import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.DBHelper;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.actors.NodeSupervisor;
import fr.acinq.eclair.wallet.adapters.LocalChannelItemAdapter;
import fr.acinq.eclair.wallet.databinding.FragmentChannelslistBinding;
import fr.acinq.eclair.wallet.models.LocalChannel;
import fr.acinq.eclair.wallet.utils.WalletUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChannelsListFragment extends Fragment {

  private LocalChannelItemAdapter mActiveChannelsAdapter;
  private LocalChannelItemAdapter mInactiveChannelsAdapter;
  private FragmentChannelslistBinding mBinding;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(false);
    mActiveChannelsAdapter = new LocalChannelItemAdapter(new ArrayList<>());
    mInactiveChannelsAdapter = new LocalChannelItemAdapter(new ArrayList<>());
  }

  @Override
  public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                           final Bundle savedInstanceState) {

    mBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_channelslist, container, false);
    mBinding.setShowInactive(false);
    mBinding.setActiveSize(0);
    mBinding.setInactiveSize(0);
    mBinding.toggleInactive.setOnClickListener(v -> {
      mBinding.setShowInactive(!mBinding.getShowInactive());
      updateInactiveChannelsList();
    });

    mBinding.activeChannelsList.setHasFixedSize(true);
    mBinding.activeChannelsList.setLayoutManager(new LinearLayoutManager(getContext()));
    mBinding.activeChannelsList.setAdapter(mActiveChannelsAdapter);

    mBinding.inactiveChannelsList.setHasFixedSize(true);
    mBinding.inactiveChannelsList.setLayoutManager(new LinearLayoutManager(getContext()));
    mBinding.inactiveChannelsList.setAdapter(mInactiveChannelsAdapter);

    return mBinding.getRoot();
  }

  @Override
  public void onResume() {
    super.onResume();
    updateActiveChannelsList();
  }

  public void updateInactiveChannelsList() {
    if (mBinding.getShowInactive()) {
      new Thread() {
        @Override
        public void run() {
          if (getContext() != null && getActivity() != null && getActivity().getApplication() != null) {
            final DBHelper dbHelper = ((App) getActivity().getApplication()).getDBHelper();
            if (dbHelper != null) {
              final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
              final List<LocalChannel> inactiveChannels = dbHelper.getInactiveChannels();
              getActivity().runOnUiThread(() -> {
                mInactiveChannelsAdapter.update(inactiveChannels,
                  WalletUtils.getPreferredFiat(prefs), WalletUtils.getPreferredCoinUnit(prefs), WalletUtils.shouldDisplayInFiat(prefs));
                mBinding.setInactiveSize(inactiveChannels.size());
              });
            }
          }
        }
      }.start();
    }
  }

  public void updateActiveChannelsList() {
    new Thread() {
      @Override
      public void run() {
        if (mActiveChannelsAdapter != null && getContext() != null && getActivity() != null) {
          final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
          final CoinUnit prefUnit = WalletUtils.getPreferredCoinUnit(prefs);
          final String fiatUnit = WalletUtils.getPreferredFiat(prefs);

          // convert to list and sort
          List<LocalChannel> channels = new ArrayList<>(NodeSupervisor.getChannelsMap().values());
          Collections.sort(channels, (c1, c2) -> Long.compare(c2.getCapacityMsat(), c1.getCapacityMsat()));

          // get sendable/receivable
          final MilliSatoshi maxReceivable = NodeSupervisor.getMaxReceivable();
          long maxSendableMsat = 0;
          long totalReceivableMsat = 0;
          long totalSendableMsat = 0;

          for (LocalChannel c : channels) {
            if (c.fundsAreUsable()) {
              maxSendableMsat = Math.max(maxSendableMsat, c.getReceivableMsat());
              totalReceivableMsat += c.getReceivableMsat();
              totalSendableMsat += c.getSendableMsat();
            }
          }

          final MilliSatoshi totalReceivable = new MilliSatoshi(totalReceivableMsat);
          final MilliSatoshi totalSendable = new MilliSatoshi(totalSendableMsat);
          final double sendReceiveRelative = totalSendableMsat + totalReceivableMsat > 0 ? (double) totalSendableMsat / (totalSendableMsat + totalReceivableMsat) * 100 : 0;

          getActivity().runOnUiThread(() -> {
            mBinding.balanceProgress.setProgress(100 - (int) sendReceiveRelative);
            mBinding.totalReceivable.setText(CoinUtils.formatAmountInUnit(totalReceivable, prefUnit, true));
            mBinding.totalReceivableFiat.setText(getString(R.string.amount_to_fiat, WalletUtils.convertMsatToFiatWithUnit(totalReceivable.amount(), fiatUnit)));
            mBinding.totalSendable.setText(CoinUtils.formatAmountInUnit(totalSendable, prefUnit, true));
            mBinding.totalSendableFiat.setText(getString(R.string.amount_to_fiat, WalletUtils.convertMsatToFiatWithUnit(totalSendable.amount(), fiatUnit)));
            mActiveChannelsAdapter.update(channels, WalletUtils.getPreferredFiat(prefs), WalletUtils.getPreferredCoinUnit(prefs), WalletUtils.shouldDisplayInFiat(prefs));
            mBinding.setActiveSize(channels.size());
          });
        }
      }
    }.start();
  }
}
