package fr.acinq.eclair.wallet.activities;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import org.greenrobot.greendao.annotation.NotNull;

import java.util.Date;

import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.fragments.PinDialog;
import fr.acinq.eclair.wallet.utils.Constants;
import scala.concurrent.duration.Duration;

public class EclairActivity extends AppCompatActivity {

  protected App app;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    app = ((App) getApplication());
  }

  protected boolean checkInit() {
    if (app == null || app.appKit == null || app.getDBHelper() == null) {
      Intent startup = new Intent(this, StartupActivity.class);
      startup.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      startup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(startup);
      return false;
    }
    return true;
  }

  protected boolean isPinRequired () {
    return !Constants.PIN_UNDEFINED_VALUE.equals(getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, MODE_PRIVATE)
      .getString(Constants.SETTING_PIN_VALUE, Constants.PIN_UNDEFINED_VALUE));
  }

  @SuppressLint("ApplySharedPref")
  protected boolean isPinCorrect (final String pinValue, @NotNull final PinDialog dialog) {
    final SharedPreferences prefs = getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, MODE_PRIVATE);
    final boolean isCorrect = prefs.getString(Constants.SETTING_PIN_VALUE, Constants.PIN_UNDEFINED_VALUE).equals(pinValue);
    if (isCorrect) {
      dialog.animateSuccess();
    } else {
      dialog.animateFailure();
    }
    return isCorrect;
  }
}

