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

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Switch;
import android.widget.Toast;

import java.io.File;
import java.security.GeneralSecurityException;
import java.text.DateFormat;
import java.util.Date;

import fr.acinq.bitcoin.BinaryData;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.fragments.PinDialog;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class PreferencesActivity extends EclairActivity implements EclairActivity.EncryptSeedCallback {

  private static final String TAG = "PrefsActivity";
  private View mPinSwitchWrapper;
  private Switch mPinSwitch;
  private SharedPreferences.OnSharedPreferenceChangeListener securityPrefsListener;
  private SharedPreferences.OnSharedPreferenceChangeListener defaultPrefsListener;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_preferences);

    mPinSwitchWrapper = findViewById(R.id.preference_pin_switch_wrapper);
    mPinSwitch = findViewById(R.id.preference_pin_switch);
    // when the switch is clicked, start the according action (remove pin, create pin)
    mPinSwitchWrapper.setOnClickListener(view -> {
      final boolean isPinDefined = isPinRequired();
      if (isPinDefined && mPinSwitch.isChecked()) {
        // The user wants to disable the PIN
        removePinValue();
      } else if (!isPinDefined && !mPinSwitch.isChecked()) {
        getApplicationContext().getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, MODE_PRIVATE).edit()
          .putBoolean(Constants.SETTING_ASK_PIN_FOR_SENSITIVE_ACTIONS, true).apply();
      } else {
        Log.d(TAG, "Pin switch check state is not up to date with the actual pin value! Switch is" + mPinSwitch.isChecked() + " / pin defined " + isPinDefined);
        mPinSwitch.setChecked(isPinRequired());
      }
    });

    securityPrefsListener = (sharedPreferences, s) -> mPinSwitch.setChecked(isPinRequired());

    defaultPrefsListener = (prefs, key) -> {
      if (Constants.SETTING_BTC_PATTERN.equals(key)) {
        CoinUtils.setCoinPattern(prefs.getString(Constants.SETTING_BTC_PATTERN, getResources().getStringArray(R.array.btc_pattern_values)[0]));
      }
    };
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (checkInit()) {
      mPinSwitch.setChecked(isPinRequired());
      getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(securityPrefsListener);
      PreferenceManager.getDefaultSharedPreferences(getBaseContext()).registerOnSharedPreferenceChangeListener(defaultPrefsListener);
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(securityPrefsListener);
    PreferenceManager.getDefaultSharedPreferences(getBaseContext()).unregisterOnSharedPreferenceChangeListener(defaultPrefsListener);
  }

  /**
   * Removes the pin value in the preferences. The user has to confirm the previous PIN before the pin is
   * removed from the preferences. If the PIN is incorrect, the action fails.
   */
  private void removePinValue() {
    final PinDialog removePinDialog = new PinDialog(PreferencesActivity.this, R.style.CustomAlertDialog, new PinDialog.PinDialogCallback() {
      @SuppressLint("ApplySharedPref")
      @Override
      public void onPinConfirm(final PinDialog dialog, final String pinValue) {
        if (isPinCorrect(pinValue, dialog)) {
          getApplicationContext().getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, MODE_PRIVATE).edit()
            .putBoolean(Constants.SETTING_ASK_PIN_FOR_SENSITIVE_ACTIONS, false).apply();
        } else {
          Toast.makeText(getApplicationContext(), "Incorrect password", Toast.LENGTH_SHORT).show();
        }
      }

      @Override
      public void onPinCancel(final PinDialog dialog) {
      }
    });
    removePinDialog.show();
  }

  public void changePassword(View view) {
    new PinDialog(PreferencesActivity.this, R.style.CustomAlertDialog, new PinDialog.PinDialogCallback() {
      @Override
      public void onPinConfirm(final PinDialog dialog, final String pinValue) {
        dialog.dismiss();
        try {
          final File datadir = new File(getFilesDir(), Constants.ECLAIR_DATADIR);
          final byte[] seed = WalletUtils.readSeedFile(datadir, pinValue);
          encryptWallet(PreferencesActivity.this, true, datadir, seed);
        } catch (GeneralSecurityException e) {
          Toast.makeText(getApplicationContext(), "Incorrect password", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
          Log.d(TAG, "failed to read seed ", e);
          Toast.makeText(getApplicationContext(), R.string.seed_read_general_failure, Toast.LENGTH_SHORT).show();
        }
      }
      @Override
      public void onPinCancel(PinDialog dialog) {}
    }).show();
  }

  @Override
  public void onEncryptSeedFailure(String message) {
    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
  }

  @Override
  public void onEncryptSeedSuccess() {
    Toast.makeText(getApplicationContext(), "Password updated", Toast.LENGTH_SHORT).show();
  }
}
