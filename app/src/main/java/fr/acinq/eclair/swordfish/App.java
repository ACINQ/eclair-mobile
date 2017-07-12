package fr.acinq.eclair.swordfish;

import android.content.Context;

import com.orm.SugarApp;

public class App extends SugarApp {

  private EclairHelper eclairInstance;

  public EclairHelper getEclairInstance() {
    return eclairInstance;
  }

  public void setEclairInstance(EclairHelper eclairInstance) {
    this.eclairInstance = eclairInstance;
  }

  @Override
  protected void attachBaseContext(Context base) {
    super.attachBaseContext(base);
  }
}
