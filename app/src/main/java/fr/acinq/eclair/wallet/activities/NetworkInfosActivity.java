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

import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.text.NumberFormat;

import fr.acinq.eclair.Globals;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.customviews.DataRow;
import fr.acinq.eclair.wallet.events.NetworkChannelsCountEvent;

public class NetworkInfosActivity extends EclairActivity implements SwipeRefreshLayout.OnRefreshListener {

  private static final String TAG = "NetworkInfosActivity";
  private DataRow mNodePublicKeyRow;
  private DataRow mNetworkChannelCount;
  private DataRow mBlockCount;
  private DataRow mFeeRate;
  private SwipeRefreshLayout mRefreshLayout;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_networkinfos);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);

    mNodePublicKeyRow = findViewById(R.id.networkinfos_nodeid);
    mNetworkChannelCount = findViewById(R.id.networkinfos_networkchannels_count);
    mBlockCount = findViewById(R.id.networkinfos_blockcount);
    mFeeRate = findViewById(R.id.networkinfos_feerate);

    mRefreshLayout = findViewById(R.id.networkinfos_swiperefresh);
    mRefreshLayout.setColorSchemeResources(R.color.primary, R.color.green, R.color.accent);
    mRefreshLayout.setOnRefreshListener(this);
  }

  @Override
  public void onRefresh() {
    refreshData();
  }

  private void refreshData() {
    mBlockCount.setValue(String.valueOf(Globals.blockCount().get()));
    mFeeRate.setValue(NumberFormat.getInstance().format(Globals.feeratesPerKw().get().block_1()) + " sat/kw");
    app.getNetworkChannelsCount();
    mRefreshLayout.setRefreshing(false);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleNetworkChannelsCountEvent(NetworkChannelsCountEvent event) {
    if (event.count == -1) {
      mNetworkChannelCount.setValue(getResources().getString(R.string.unknown));
    } else {
      mNetworkChannelCount.setValue(Integer.toString(event.count));
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (checkInit()) {
      if (!EventBus.getDefault().isRegistered(this)) {
        EventBus.getDefault().register(this);
      }
      mNodePublicKeyRow.setValue(app.nodePublicKey());
      refreshData();
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    EventBus.getDefault().unregister(this);
  }
}
