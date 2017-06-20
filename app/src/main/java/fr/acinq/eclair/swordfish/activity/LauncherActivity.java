package fr.acinq.eclair.swordfish.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import fr.acinq.eclair.swordfish.EclairHelper;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.StartupTask;

public class LauncherActivity extends AppCompatActivity implements StartupTask.AsyncSetupResponse {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_launcher);
    new StartupTask(this, getFilesDir()).execute();
  }

  @Override
  public void processFinish(String output) {
    Intent intent = new Intent(this, HomeActivity.class);
    startActivity(intent);
    finish();
  }
}

