package fr.acinq.eclair.swordfish.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import fr.acinq.eclair.swordfish.EclairHelper;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.tasks.StartupTask;

public class LauncherActivity extends AppCompatActivity implements StartupTask.AsyncSetupResponse {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_launcher);
    new StartupTask(this, getFilesDir()).execute();
  }

  @Override
  public void processFinish(String output) {
    if (EclairHelper.hasInstance()) {
      Intent intent = new Intent(this, HomeActivity.class);
      startActivity(intent);
      finish();
    } else {
      Toast.makeText(this, "Could not start eclair", Toast.LENGTH_LONG).show();
    }
  }
}

