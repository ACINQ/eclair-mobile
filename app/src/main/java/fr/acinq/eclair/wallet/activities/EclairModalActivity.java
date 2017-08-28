package fr.acinq.eclair.wallet.activities;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;

import fr.acinq.eclair.wallet.App;

public class EclairModalActivity extends Activity {

  protected App app;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    app = ((App) getApplication());
  }
}
