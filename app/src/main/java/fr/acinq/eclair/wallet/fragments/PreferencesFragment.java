package fr.acinq.eclair.wallet.fragments;

import android.os.Bundle;
import android.support.v7.preference.PreferenceFragmentCompat;

import fr.acinq.eclair.wallet.R;

public class PreferencesFragment extends PreferenceFragmentCompat {

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    // Load the preferences from an XML resource
    addPreferencesFromResource(R.xml.preferences);
  }
}
