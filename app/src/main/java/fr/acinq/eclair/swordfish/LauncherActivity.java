package fr.acinq.eclair.swordfish;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;

import akka.actor.ActorRef;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.io.Switchboard;
import scala.Option;

public class LauncherActivity extends AppCompatActivity implements StartupTask.AsyncSetupResponse {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_launcher);

    SharedPreferences pref = getSharedPreferences("ActivityPREF", Context.MODE_PRIVATE);
    if(pref.getBoolean("activity_executed", false)){
      Intent intent = new Intent(this, ChannelActivity.class);
      startActivity(intent);
      finish();
    } else {
      new StartupTask(this, getApplicationContext()).execute();
      SharedPreferences.Editor ed = pref.edit();
      ed.putBoolean("activity_executed", true);
      ed.commit();
    }
  }

  @Override
  public void processFinish(String output) {
    Intent intent = new Intent(this, ChannelActivity.class);
    startActivity(intent);
  }
}

