package fr.acinq.eclair.swordfish.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import fr.acinq.eclair.Globals;
import fr.acinq.eclair.swordfish.EclairEventService;
import fr.acinq.eclair.swordfish.EclairHelper;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.customviews.DataRow;

public class SettingsActivity extends AppCompatActivity {

  private DataRow mNodePublicKeyRow;
  private DataRow mAliasRow;
  private DataRow mNetworkChannelCount;
  private DataRow mNetworkNodesCount;
  private DataRow mBlockCount;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_settings);

    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);

    mNodePublicKeyRow = (DataRow) findViewById(R.id.settings_nodeid);
    mAliasRow = (DataRow) findViewById(R.id.settings_nodealias);
    mNetworkNodesCount = (DataRow) findViewById(R.id.settings_networknodes_count);
    mNetworkChannelCount = (DataRow) findViewById(R.id.settings_networkchannels_count);
    mBlockCount = (DataRow) findViewById(R.id.settings_blockcount);
  }

  public void goToNetworkChannels(View view) {
    Intent intent = new Intent(this, NetworkChannelsActivity.class);
    startActivity(intent);
  }

  public void goToNetworkNodes(View view) {
    Intent intent = new Intent(this, NetworkNodesActivity.class);
    startActivity(intent);
  }

  public void settings_refreshCount(View view) {
    mBlockCount.setValue(String.valueOf(Globals.blockCount().get()));
  }

  @Override
  public void onResume() {
    super.onResume();
    mNodePublicKeyRow.setValue(EclairHelper.nodePublicKey(getApplicationContext()));
    mAliasRow.setValue(EclairHelper.nodeAlias(getApplicationContext()));
    mNetworkChannelCount.setValue(Integer.toString(EclairEventService.channelAnnouncementMap.size()));
    mNetworkNodesCount.setValue(Integer.toString(EclairEventService.nodeAnnouncementMap.size()));
    mBlockCount.setValue(String.valueOf(Globals.blockCount().get()));
  }
}
