package fr.acinq.eclair.swordfish;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.orm.SugarApp;

import fr.acinq.eclair.swordfish.activity.LauncherActivity;

public class App extends SugarApp {

  public final static String TAG = "App";

  private EclairHelper eclairInstance;

  public EclairHelper getEclairInstance() throws EclairStartException {
    if (eclairInstance == null) {
      Log.d(TAG, "Eclair is needed but is not started, redirecting to Launcher");
      Intent intent = new Intent(this, LauncherActivity.class);
      intent.putExtra(LauncherActivity.EXTRA_AUTOSTART, true);
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);
      throw new EclairStartException();
    } else {
      return eclairInstance;
    }
  }

  public void setEclairInstance(EclairHelper eclairInstance) {
    this.eclairInstance = eclairInstance;
  }

  @Override
  protected void attachBaseContext(Context base) {
    super.attachBaseContext(base);
  }
}
