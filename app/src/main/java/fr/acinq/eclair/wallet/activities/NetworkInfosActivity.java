package fr.acinq.eclair.wallet.activities;

import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import fr.acinq.eclair.Globals;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.customviews.DataRow;
import fr.acinq.eclair.wallet.events.NetworkChannelsCountEvent;
import fr.acinq.eclair.wallet.events.NetworkNodesCountEvent;

public class NetworkInfosActivity extends EclairActivity implements SwipeRefreshLayout.OnRefreshListener {

  private static final String TAG = "NetworkInfosActivity";
  private DataRow mNodePublicKeyRow;
  private DataRow mNetworkChannelCount;
  private DataRow mNetworkNodesCount;
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
    mNetworkNodesCount = findViewById(R.id.networkinfos_networknodes_count);
    mNetworkChannelCount = findViewById(R.id.networkinfos_networkchannels_count);
    mBlockCount = findViewById(R.id.networkinfos_blockcount);
    mFeeRate = findViewById(R.id.networkinfos_feerate);

    mRefreshLayout = findViewById(R.id.networkinfos_swiperefresh);
    mRefreshLayout.setColorSchemeResources(R.color.colorPrimary, R.color.green, R.color.colorAccent);
    mRefreshLayout.setOnRefreshListener(this);
  }

  @Override
  public void onRefresh() {
    refreshData();
  }

  private void refreshData() {
    mBlockCount.setValue(String.valueOf(Globals.blockCount().get()));
    mFeeRate.setValue(Globals.feeratePerKw().toString());
    app.getNetworkNodesCount();
    app.getNetworkChannelsCount();
    mRefreshLayout.setRefreshing(false);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleNetworkNodesCountEvent(NetworkNodesCountEvent event) {
    if (event.count == -1) {
      mNetworkNodesCount.setValue(getResources().getString(R.string.unknown));
    } else {
      mNetworkNodesCount.setValue(Integer.toString(event.count));
    }
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
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this);
    }
    mNodePublicKeyRow.setValue(app.nodePublicKey());
    refreshData();
  }

  @Override
  protected void onPause() {
    super.onPause();
    EventBus.getDefault().unregister(this);
  }
}
