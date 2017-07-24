package fr.acinq.eclair.wallet.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;

import fr.acinq.eclair.Globals;
import fr.acinq.eclair.wallet.EclairEventService;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.customviews.DataRow;

public class NetworkInfosActivity extends EclairActivity {

  private static final String TAG = "NetworkInfosActivity";
  private TextView mZipLocationView;
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

    // mZipLocationView = (TextView) findViewById(R.id.networkinfos_zip_path);
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

  //  private boolean isExternalStorageWritable() {
  //    String state = Environment.getExternalStorageState();
  //    if (Environment.MEDIA_MOUNTED.equals(state)) {
  //      return true;
  //    }
  //    return false;
  //  }
  //
  //  private void zipFailed() {
  //    Toast.makeText(this, R.string.zip_datadir_toast_failure, Toast.LENGTH_SHORT).show();
  //  }

  // disabled for now
  //  public void zipDatadir(View view) {
  //    if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
  //      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_WRITE);
  //    } else {
  //      doZipDatadir();
  //    }
  //  }

  //  private void doZipDatadir() {
  //    if (!isExternalStorageWritable()) {
  //      zipFailed();
  //      return;
  //    }
  //    File outputZipDir = getZipDirectory();
  //    try {
  //      outputZipDir.mkdirs();
  //      @SuppressLint("SimpleDateFormat")
  //      SimpleDateFormat zipDateFormat = new SimpleDateFormat("yyyy-MM-dd-HHmmss");
  //      File outputZipFile = new File(outputZipDir, App.DATADIR_NAME + "_" + zipDateFormat.format(new Date()) + ".zip");
  //      ZipUtil.pack(new File(getApplicationContext().getFilesDir(), App.DATADIR_NAME), outputZipFile);
  //      Toast.makeText(this, R.string.zip_datadir_toast_success, Toast.LENGTH_SHORT).show();
  //      Log.i(TAG, "Successfully extracted datadir to " + outputZipFile.getAbsolutePath());
  //    } catch (Exception e) {
  //      Log.e(TAG, "Could not extract datadir", e);
  //    }
  //  }

  //  private static int MY_PERMISSIONS_REQUEST_WRITE = 0;
  //
  //  private File getZipDirectory() {
  //    return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
  //  }
  //
  //  @Override
  //  public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
  //    if (requestCode == MY_PERMISSIONS_REQUEST_WRITE) {
  //      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
  //        doZipDatadir();
  //      } else {
  //        zipFailed();
  //      }
  //      return;
  //    }
  //  }

  @Override
  public void onResume() {
    super.onResume();
    mNodePublicKeyRow.setValue(app.nodePublicKey());
    mNetworkChannelCount.setValue(Integer.toString(EclairEventService.channelAnnouncementMap.size()));
    mNetworkNodesCount.setValue(Integer.toString(EclairEventService.nodeAnnouncementMap.size()));
    mBlockCount.setValue(String.valueOf(Globals.blockCount().get()));
    // mZipLocationView.setText(getString(R.string.zip_datadir_prefix) + getZipDirectory().getAbsolutePath());
    mFeeRate.setValue(Globals.feeratePerKw().toString());
  }
}
