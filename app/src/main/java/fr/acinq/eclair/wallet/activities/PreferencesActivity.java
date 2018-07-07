/*
 * Copyright 2018 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.eclair.wallet.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;
import android.support.v7.preference.PreferenceManager;

import java.util.List;

import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.utils.Constants;

public class PreferencesActivity extends PreferenceActivity {

  private static final String TAG = PreferencesActivity.class.getSimpleName();
  private SharedPreferences.OnSharedPreferenceChangeListener defaultPrefsListener;

  @Override
  public void onBuildHeaders(List<Header> target) {
    loadHeadersFromResource(R.xml.preference_headers, target);
  }

  @Override
  protected boolean isValidFragment(String fragmentName) {
    return LightningSettingsFragment.class.getName().equals(fragmentName)
      || GeneralSettingsFragment.class.getName().equals(fragmentName);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
    super.onCreate(savedInstanceState, persistentState);
    defaultPrefsListener = (prefs, key) -> {
      if (Constants.SETTING_BTC_PATTERN.equals(key)) {
        CoinUtils.setCoinPattern(prefs.getString(Constants.SETTING_BTC_PATTERN, getResources().getStringArray(R.array.btc_pattern_values)[3]));
      }
    };
  }

  @Override
  public void onHeaderClick(Header header, int position) {
    super.onHeaderClick(header, position);
    if (header.id == R.id.header_security) {
      startActivity(new Intent(getBaseContext(), SecuritySettingsActivity.class));
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).registerOnSharedPreferenceChangeListener(defaultPrefsListener);
  }

  @Override
  protected void onPause() {
    super.onPause();
    PreferenceManager.getDefaultSharedPreferences(getBaseContext()).unregisterOnSharedPreferenceChangeListener(defaultPrefsListener);
  }

  public static class GeneralSettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = GeneralSettingsFragment.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.preference_general);
    }

    @Override
    public void onResume() {
      super.onResume();
      getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
      super.onPause();
      getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
      if (Constants.SETTING_BTC_PATTERN.equals(key)) {
        CoinUtils.setCoinPattern(prefs.getString(Constants.SETTING_BTC_PATTERN, getResources().getStringArray(R.array.btc_pattern_values)[3]));
      }
    }
  }

  public static class LightningSettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.preference_lightning);
    }
  }
}
