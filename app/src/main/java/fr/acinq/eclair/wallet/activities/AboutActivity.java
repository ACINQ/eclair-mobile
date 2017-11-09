package fr.acinq.eclair.wallet.activities;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.widget.TextView;

import fr.acinq.eclair.wallet.R;

public class AboutActivity extends EclairActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_about);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);
    ((TextView) findViewById(R.id.about_general)).setText(Html.fromHtml(getString(R.string.about_general_text, app.getVersion())));
    ((TextView) findViewById(R.id.about_limitations)).setText(Html.fromHtml(getString(R.string.about_limitations_text)));
    ((TextView) findViewById(R.id.about_acinq)).setText(Html.fromHtml(getString(R.string.about_acinq_text)));
  }
}
