package fr.acinq.eclair.swordfish.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import fr.acinq.eclair.Globals;
import fr.acinq.eclair.swordfish.App;
import fr.acinq.eclair.swordfish.EclairEventService;
import fr.acinq.eclair.swordfish.EclairHelper;
import fr.acinq.eclair.swordfish.EclairStartException;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.customviews.DataRow;

public class SettingsActivity extends AppCompatActivity {

  private static final String TAG = "SettingsActivity";
  private TextView mZipLocationView;
  private DataRow mNodePublicKeyRow;
  private DataRow mNetworkChannelCount;
  private DataRow mNetworkNodesCount;
  private DataRow mBlockCount;
  private DataRow mFeeRate;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_settings);

    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);

    mZipLocationView = (TextView) findViewById(R.id.settings_zip_path);
    mNodePublicKeyRow = (DataRow) findViewById(R.id.settings_nodeid);
    mNetworkNodesCount = (DataRow) findViewById(R.id.settings_networknodes_count);
    mNetworkChannelCount = (DataRow) findViewById(R.id.settings_networkchannels_count);
    mBlockCount = (DataRow) findViewById(R.id.settings_blockcount);
    mFeeRate = (DataRow) findViewById(R.id.settings_feerate);
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
  public void settings_refreshFeerate(View view) {
    mFeeRate.setValue(Globals.feeratePerKw().toString());
  }

  private boolean isExternalStorageWritable() {
    String state = Environment.getExternalStorageState();
    if (Environment.MEDIA_MOUNTED.equals(state)) {
      return true;
    }
    return false;
  }

  private void zipFailed() {
    Toast.makeText(this, R.string.zip_datadir_toast_failure, Toast.LENGTH_SHORT).show();
  }

  public void zipDatadir(View view) {
    if (!isExternalStorageWritable()) {
      zipFailed();
      return;
    }
    File outputZipDir = getZipDirectory();
    try {
      outputZipDir.mkdirs();
      @SuppressLint("SimpleDateFormat")
      SimpleDateFormat zipDateFormat = new SimpleDateFormat("yyyy-MM-dd-HHmmss");
      File outputZipFile = new File(outputZipDir, EclairHelper.DATADIR_NAME + "_" + zipDateFormat.format(new Date()) + ".zip");
      ZipUtil.pack(new File(getApplicationContext().getFilesDir(), EclairHelper.DATADIR_NAME), outputZipFile);
      Toast.makeText(this, R.string.zip_datadir_toast_success, Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      Log.e(TAG, "Could not extract datadir", e);
    }
  }

  private File getZipDirectory() {
    return new File(getBaseContext().getExternalFilesDir(null), "extracted-datadirs");
  }

  @Override
  public void onResume() {
    super.onResume();

    try {
      EclairHelper eclairHelper = ((App) getApplication()).getEclairInstance();
      mNodePublicKeyRow.setValue(eclairHelper.nodePublicKey());
      mNetworkChannelCount.setValue(Integer.toString(EclairEventService.channelAnnouncementMap.size()));
      mNetworkNodesCount.setValue(Integer.toString(EclairEventService.nodeAnnouncementMap.size()));
      mBlockCount.setValue(String.valueOf(Globals.blockCount().get()));
      mZipLocationView.setText(getString(R.string.zip_datadir_prefix) + getZipDirectory().getAbsolutePath());
      mFeeRate.setValue(Globals.feeratePerKw().toString());
    } catch (EclairStartException e) {
      finish();
    }
  }
}
