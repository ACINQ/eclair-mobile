package fr.acinq.eclair.wallet.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import fr.acinq.eclair.wallet.fragments.PreferencesFragment;

public class PreferencesActivity extends AppCompatActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Display the fragment as the main content.
    getFragmentManager().beginTransaction()
      .replace(android.R.id.content, new PreferencesFragment())
      .commit();
  }

}
