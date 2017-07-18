package fr.acinq.eclair.wallet.activity;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import fr.acinq.eclair.wallet.App;

public class EclairActivity extends AppCompatActivity {

  protected App app;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    app = ((App) getApplication());
  }
}

