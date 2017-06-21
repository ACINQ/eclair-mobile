package fr.acinq.eclair.swordfish.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.utils.Validators;

public class ChannelInputActivity extends Activity {

  public static final String EXTRA_NEWHOSTURI = "fr.acinq.eclair.swordfish.NEWHOSTURI";
  private static final String TAG = "ChannelInputActivity";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_channel_input);
  }

  public void openChannelURIScanner(View view) {
    IntentIntegrator integrator = new IntentIntegrator(this);
    integrator.setOrientationLocked(false);
    integrator.setCaptureActivity(ScanActivity.class);
    integrator.setBeepEnabled(false);
    integrator.initiateScan();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Log.d(TAG, "Got a result with code " + requestCode + "/" + resultCode);
    IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
    if (result != null /*&& requestCode == */ && resultCode == RESULT_OK) {
      if (result.getContents() == null) {
        Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show();
      } else {
        goToOpenChannelActivity(result.getContents());
      }
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  private void goToOpenChannelActivity(String uri) {
    if (Validators.HOST_REGEX.matcher(uri).matches()) {
      Intent intent = new Intent(this, OpenChannelActivity.class);
      intent.putExtra(EXTRA_NEWHOSTURI, uri);
      startActivity(intent);
    } else {
      Toast.makeText(this, "Invalid URI", Toast.LENGTH_SHORT).show();
    }
  }
}
