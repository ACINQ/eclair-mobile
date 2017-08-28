package fr.acinq.eclair.wallet.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.View;

import fr.acinq.eclair.Globals;
import fr.acinq.eclair.wallet.EclairEventService;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.customviews.DataRow;

public class NetworkInfosActivity extends EclairActivity {

  private static final String TAG = "NetworkInfosActivity";
  private DataRow mNodePublicKeyRow;
  private DataRow mNetworkChannelCount;
  private DataRow mNetworkNodesCount;
  private DataRow mBlockCount;
  private DataRow mFeeRate;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_networkinfos);

    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);

    mNodePublicKeyRow = (DataRow) findViewById(R.id.networkinfos_nodeid);
    mNetworkNodesCount = (DataRow) findViewById(R.id.networkinfos_networknodes_count);
    mNetworkChannelCount = (DataRow) findViewById(R.id.networkinfos_networkchannels_count);
    mBlockCount = (DataRow) findViewById(R.id.networkinfos_blockcount);
    mFeeRate = (DataRow) findViewById(R.id.networkinfos_feerate);
  }

  public void goToNetworkChannels(View view) {
    Intent intent = new Intent(this, NetworkChannelsActivity.class);
    startActivity(intent);
  }

  public void goToNetworkNodes(View view) {
    Intent intent = new Intent(this, NetworkNodesActivity.class);
    startActivity(intent);
  }

  public void networkinfos_refreshCount(View view) {
    mBlockCount.setValue(String.valueOf(Globals.blockCount().get()));
  }

  public void networkinfos_refreshFeerate(View view) {
    mFeeRate.setValue(Globals.feeratePerKw().toString());
  }

  @Override
  public void onResume() {
    super.onResume();
    mNodePublicKeyRow.setValue(app.nodePublicKey());
    mNetworkChannelCount.setValue(Integer.toString(EclairEventService.channelAnnouncementMap.size()));
    mNetworkNodesCount.setValue(Integer.toString(EclairEventService.nodeAnnouncementMap.size()));
    mBlockCount.setValue(String.valueOf(Globals.blockCount().get()));
    mFeeRate.setValue(Globals.feeratePerKw().toString());
  }
}
