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
import android.preference.*;
import android.support.v7.app.AlertDialog;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.actors.NodeSupervisor;
import fr.acinq.eclair.wallet.services.CheckElectrumWorker;
import fr.acinq.eclair.wallet.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreferencesActivity extends PreferenceActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getFragmentManager().beginTransaction().replace(android.R.id.content, new GeneralSettingsFragment()).commit();
  }

  public static class GeneralSettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final Logger log = LoggerFactory.getLogger(GeneralSettingsFragment.class);

    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.preferences);
      // --- override "enable reception with lightning" switch button: can only be enabled if all conditions are met
      if (!NodeSupervisor.canReceivePayments()) {
        disableCanReceive();
      }
      findPreference("enable_lightning_inbound_payments").setOnPreferenceChangeListener((preference, newValue) -> {
        final boolean canReceive = NodeSupervisor.canReceivePayments();
        log.debug("on preference change, new_value={}, canReceive=", newValue, canReceive);
        if (canReceive) {
          if (newValue instanceof Boolean && (Boolean) newValue) {
            new AlertDialog.Builder(getActivity(), R.style.CustomDialog)
              .setTitle(R.string.prefs_lightning_inbound_warning_title)
              .setMessage(R.string.prefs_lightning_inbound_warning_message)
              .setPositiveButton(R.string.btn_ok, null)
              .show();
            CheckElectrumWorker.scheduleLongDelay();
          }
          return true; // that is, preference will be updated with the value input by the user
        } else {
          // user cannot receive over LN, disable the feature
          disableCanReceive();
          if (getActivity() != null) {
            new AlertDialog.Builder(getActivity(), R.style.CustomDialog)
              .setTitle(R.string.prefs_lightning_error_not_authorized_title)
              .setMessage(getString(R.string.prefs_lightning_error_not_authorized_message, NodeSupervisor.MIN_REMOTE_TO_SELF_DELAY))
              .setPositiveButton(R.string.btn_ok, null)
              .show();
          }
          return false; // user action is ignored
        }
      });
      // --- advanced settings button sending to specific activities
      findPreference("security_key").setOnPreferenceClickListener(v -> {
        startActivity(new Intent(getActivity().getApplicationContext(), SecuritySettingsActivity.class));
        return true;
      });
      findPreference("backup_channel_key").setOnPreferenceClickListener(v -> {
        startActivity(new Intent(getActivity().getApplicationContext(), ChannelsBackupSettingsActivity.class));
        return true;
      });
      findPreference("logging_conf_key").setOnPreferenceClickListener(v -> {
        startActivity(new Intent(getActivity().getApplicationContext(), LogsSettingsActivity.class));
        return true;
      });
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

    private void disableCanReceive() {
      ((SwitchPreference) findPreference("enable_lightning_inbound_payments")).setChecked(false);
//      PreferenceManager.getDefaultSharedPreferences(getActivity())
//        .edit().putBoolean(Constants.SETTING_ENABLE_LIGHTNING_INBOUND_PAYMENTS, false).apply();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
      if (Constants.SETTING_BTC_PATTERN.equals(key)) {
        CoinUtils.setCoinPattern(prefs.getString(Constants.SETTING_BTC_PATTERN, getResources().getStringArray(R.array.btc_pattern_values)[3]));
      }
    }
  }
}
