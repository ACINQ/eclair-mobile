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

import android.app.Dialog;
import androidx.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.widget.Toast;

import com.google.common.base.Strings;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.net.InetSocketAddress;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityNetworkInfosBinding;
import fr.acinq.eclair.wallet.events.NetworkChannelsCountEvent;
import fr.acinq.eclair.wallet.events.XpubEvent;
import fr.acinq.eclair.wallet.fragments.CustomElectrumServerDialog;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkInfosActivity extends EclairActivity implements SwipeRefreshLayout.OnRefreshListener {

  private ActivityNetworkInfosBinding mBinding;
  private CustomElectrumServerDialog mElectrumDialog;
  private final Logger log = LoggerFactory.getLogger(NetworkInfosActivity.class);

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_network_infos);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);

    mBinding.swipeRefresh.setColorSchemeResources(R.color.primary, R.color.green, R.color.accent);
    mBinding.swipeRefresh.setOnRefreshListener(this);
    mBinding.networkChannelsCount.actionButton.setOnClickListener(v -> deleteNetworkDB());
    mBinding.electrumAddress.actionButton.setOnClickListener(v -> setCustomElectrum());
    mBinding.xpub.actionButton.setOnClickListener(v -> deleteElectrumDB());
  }

  @Override
  public void onRefresh() {
    refreshData();
  }

  private void refreshData() {
    final long blockHeight = WalletUtils.getBlockHeight(getApplicationContext());
    if (blockHeight == 0) {
      mBinding.blockCount.setValue(getString(R.string.networkinfos_block_unknown));
    } else if (app.getBlockTimestamp() == 0) {
      mBinding.blockCount.setValue(NumberFormat.getInstance().format(blockHeight));
    } else {
      mBinding.blockCount.setHtmlValue(getString(R.string.networkinfos_block,
        NumberFormat.getInstance().format(blockHeight), // block height
        DateFormat.getDateTimeInstance().format(new Date(app.getBlockTimestamp() * 1000)))); // block timestamp
    }

    final String customElectrumServer = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.CUSTOM_ELECTRUM_SERVER, "");
    final InetSocketAddress currentElectrumServer = app.getElectrumServerAddress();
    if (currentElectrumServer == null || Strings.isNullOrEmpty(currentElectrumServer.toString())) {
      // not yet connected...
      if (Strings.isNullOrEmpty(customElectrumServer)) {
        mBinding.electrumAddress.setValue(getString(R.string.networkinfos_electrum_address_connecting));
      } else {
        mBinding.electrumAddress.setValue(getString(R.string.networkinfos_electrum_address_connecting_to_custom, customElectrumServer));
      }
    } else {
      mBinding.electrumAddress.setValue(currentElectrumServer.toString());
    }

    if (Strings.isNullOrEmpty(customElectrumServer)) {
      mBinding.electrumAddress.setActionLabel(getString(R.string.networkinfos_electrum_address_set_custom));
    } else {
      mBinding.electrumAddress.setActionLabel(getString(R.string.networkinfos_electrum_address_change_custom));
    }

    if (app != null && app.appKit != null) {
      mBinding.feeRate.setValue(NumberFormat.getInstance().format(app.appKit.eclairKit.nodeParams().onChainFeeConf().feeEstimator().getFeeratePerKw(1)) + " sat/kw");
    }
    app.getNetworkChannelsCount();
    mBinding.swipeRefresh.setRefreshing(false);
  }

  @Override
  public void onResume() {
    super.onResume();
    if (checkInit()) {
      if (!EventBus.getDefault().isRegistered(this)) {
        EventBus.getDefault().register(this);
      }
      app.getXpubFromWallet();
      mBinding.nodeId.setValue(app.nodePublicKey());
      refreshData();
    }
  }

  @Override
  protected void onPause() {
    if (mElectrumDialog != null) {
      mElectrumDialog.dismiss();
    }
    EventBus.getDefault().unregister(this);
    super.onPause();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleXpubEvent(XpubEvent event) {
    if (event == null || event.xpub == null) {
      mBinding.xpub.setValue(getString(R.string.unknown));
    } else {
      mBinding.xpub.setValue(event.xpub.xpub() + "\n\n" + event.xpub.path());
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleNetworkChannelsCountEvent(NetworkChannelsCountEvent event) {
    if (event.count == -1) {
      mBinding.networkChannelsCount.setValue(getResources().getString(R.string.unknown));
    } else {
      mBinding.networkChannelsCount.setValue(Integer.toString(event.count));
    }
  }

  private void deleteNetworkDB() {
    final Dialog confirm = getCustomDialog(R.string.networkinfos_networkdb_confirm)
      .setPositiveButton(R.string.btn_ok, (dialog, which) ->
        new Thread() {
          @Override
          public void run() {
            final File networkDB = WalletUtils.getNetworkDBFile(getApplicationContext());
            if (networkDB.delete()) {
              runOnUiThread(() -> {
                Toast.makeText(getApplicationContext(), R.string.networkinfos_networkdb_toast, Toast.LENGTH_SHORT).show();
                restart();
              });
            }
          }
        }.start())
      .setNegativeButton(R.string.btn_cancel, (dialog, which) -> {
      }).create();
    confirm.show();
  }

  private void setCustomElectrum() {
    mElectrumDialog = new CustomElectrumServerDialog(NetworkInfosActivity.this, this::handleCustomElectrumSubmit);
    mElectrumDialog.show();
  }

  /**
   * Displays a message to the user and restart the app after 3s.
   */
  private void handleCustomElectrumSubmit(final String serverAddress) {
    final String message = Strings.isNullOrEmpty(serverAddress)
      ? getString(R.string.networkinfos_electrum_confirm_message_default)
      : getString(R.string.networkinfos_electrum_confirm_message, serverAddress);
    getCustomDialog(message).setCancelable(false).show();
    new Handler().postDelayed(this::restart, 3000);
  }

  private void deleteElectrumDB() {
    final Dialog confirm = getCustomDialog(R.string.networkinfos_electrumdb_confirm)
      .setPositiveButton(R.string.btn_ok, (dialog, which) ->
        new Thread() {
          @Override
          public void run() {
            final File walletDB = WalletUtils.getWalletDBFile(getApplicationContext());
            if (walletDB.delete()) {
              runOnUiThread(() -> {
                app.getDBHelper().deleteAllOnchainTxs();
                Toast.makeText(getApplicationContext(), R.string.networkinfos_electrumdb_toast, Toast.LENGTH_SHORT).show();
                restart();
              });
            }
          }
        }.start())
      .setNegativeButton(R.string.btn_cancel, (dialog, which) -> {
      }).create();
    confirm.show();
  }
}
