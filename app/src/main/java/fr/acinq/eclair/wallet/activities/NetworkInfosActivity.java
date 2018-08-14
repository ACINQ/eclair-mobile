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

import android.app.Dialog;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;

import fr.acinq.eclair.Globals;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityNetworkInfosBinding;
import fr.acinq.eclair.wallet.events.NetworkChannelsCountEvent;
import fr.acinq.eclair.wallet.events.XpubEvent;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class NetworkInfosActivity extends EclairActivity implements SwipeRefreshLayout.OnRefreshListener {

  private static final String TAG = NetworkInfosActivity.class.getSimpleName();
  private ActivityNetworkInfosBinding mBinding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_network_infos);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);

    // refresh
    mBinding.swipeRefresh.setColorSchemeResources(R.color.primary, R.color.green, R.color.accent);
    mBinding.swipeRefresh.setOnRefreshListener(this);
    // delete db
    mBinding.deleteNetworkDB.actionButton.setOnClickListener(v -> deleteNetworkDB());
  }

  @Override
  public void onRefresh() {
    refreshData();
  }

  private void refreshData() {
    mBinding.blockCount.setValue(String.valueOf(Globals.blockCount().get()));
    mBinding.blockTimestamp.setValue(DateFormat.getDateTimeInstance().format(new Date(app.getBlockTimestamp() * 1000)));
    mBinding.electrumAddress.setValue(app.getElectrumServerAddress());
    mBinding.feeRate.setValue(NumberFormat.getInstance().format(Globals.feeratesPerKw().get().block_1()) + " sat/kw");
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
    super.onPause();
    EventBus.getDefault().unregister(this);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleRawDataEvent(XpubEvent event) {
    if (event == null || event.xpub == null) {
      mBinding.xpub.setValue("Could not get wallet xpub.");
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
}
