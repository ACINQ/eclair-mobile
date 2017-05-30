package fr.acinq.eclair.swordfish.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.StartupTask;

public class LauncherActivity extends AppCompatActivity implements StartupTask.AsyncSetupResponse {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_launcher);

    SharedPreferences pref = getSharedPreferences("ActivityPREF", Context.MODE_PRIVATE);
//    if (pref.getBoolean("activity_executed", false) && EclairHelper.hasInstance()) {
//      Intent intent = new Intent(this, ChannelActivity.class);
//      startActivity(intent);
//      finish();
//    } else {
      new StartupTask(this, getApplicationContext()).execute();
      SharedPreferences.Editor ed = pref.edit();
      ed.putBoolean("activity_executed", true);
      ed.commit();
//    }
  }

  @Override
  public void processFinish(String output) {
    Intent intent = new Intent(this, ChannelActivity.class);
    startActivity(intent);
  }
}

