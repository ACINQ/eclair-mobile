package fr.acinq.eclair.wallet.activities;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;

import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.fragments.PinDialog;
import fr.acinq.eclair.wallet.fragments.PreferencesFragment;
import fr.acinq.eclair.wallet.utils.Constants;

public class PreferencesActivity extends EclairActivity {

  private static final String TAG = "PrefsActivity";
  private View mPinSwitchWrapper;
  private Switch mPinSwitch;
  private TextView mPinInfo;
  private SharedPreferences.OnSharedPreferenceChangeListener securityPrefsListener;
  private SharedPreferences.OnSharedPreferenceChangeListener defaultPrefsListener;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_preferences);

//    getFragmentManager().beginTransaction()
//      .replace(R.id.preference_fragment_placeholder, new PreferencesFragment())
//      .commit();

    mPinSwitchWrapper = findViewById(R.id.preference_pin_switch_wrapper);
    mPinSwitch = findViewById(R.id.preference_pin_switch);
    // when the switch is clicked, start the according action (remove pin, create pin)
    mPinSwitchWrapper.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(final View view) {
        final boolean isPinDefined = isPinRequired();
        if (isPinDefined && mPinSwitch.isChecked()) {
          // The user wants to disable the PIN
          removePinValue();
        } else if (!isPinDefined && !mPinSwitch.isChecked()) {
          setNewPinValue();
        } else {
          Log.w(TAG, "Pin switch check state is not up to date with the actual pin value!");
          Log.w(TAG, "Switch is" + mPinSwitch.isChecked() + " / pin defined " + isPinDefined);
          // force refresh the state of the PIN displays with the actual values from the preferences
          refreshPinDisplays(getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, MODE_PRIVATE));
        }
      }
    });
    mPinInfo = findViewById(R.id.preference_pin_info);

    securityPrefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
      @SuppressLint("SetTextI18n")
      @Override
      public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        refreshPinDisplays(sharedPreferences);
      }
    };

    defaultPrefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
      @SuppressLint("SetTextI18n")
      @Override
      public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (Constants.SETTING_BTC_PATTERN.equals(key)) {
          CoinUtils.setCoinPattern(prefs.getString(Constants.SETTING_BTC_PATTERN, getResources().getStringArray(R.array.btc_pattern_values)[0]));
        }
      }
    };
  }

  /**
   * Refresh the Switch state and the last update TextView with the current values stored in preferences.
   * @param sharedPreferences
   */
  private void refreshPinDisplays(final SharedPreferences sharedPreferences) {
    final String pinValue = sharedPreferences.getString(Constants.SETTING_PIN_VALUE, Constants.PIN_UNDEFINED_VALUE);
    final long lastUpdateMillis = sharedPreferences.getLong(Constants.SETTING_PIN_LAST_UPDATE, 0);
    mPinSwitch.setChecked(!Constants.PIN_UNDEFINED_VALUE.equals(pinValue));
    if (lastUpdateMillis != 0) {
      mPinInfo.setText(getString(R.string.prefs_pin_lastupdate) + " " + DateFormat.getDateTimeInstance().format(lastUpdateMillis));
    }
  }

  /**
   * Opens a Dialog window to set the PIN to a new value. If the value is correct, saves the value in preferences.
   */
  private void setNewPinValue() {
    final PinDialog newPinDialog = new PinDialog(PreferencesActivity.this, R.style.CustomAlertDialog, new PinDialog.PinDialogCallback() {
      @SuppressLint("ApplySharedPref")
      @Override
      public void onPinConfirm(final PinDialog pNewPinDialog, final String newPinValue) {
        try {
          if (newPinValue != null && newPinValue.length() == Constants.PIN_LENGTH) {
            Integer.parseInt(newPinValue); // check that the pin is a digit
            final PinDialog confirmNewPinDialog = new PinDialog(PreferencesActivity.this, R.style.CustomAlertDialog, new PinDialog.PinDialogCallback() {
              @Override
              public void onPinConfirm(final PinDialog pConfirmPinDialog, final String confirmPinValue) {
                // PINs must match
                final SharedPreferences prefs = getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, MODE_PRIVATE);
                if (!newPinValue.equals(confirmPinValue)) {
                  Toast.makeText(getApplicationContext(), R.string.pindialog_error_donotmatch, Toast.LENGTH_LONG).show();
                }
                // 2nd check and final before setting new PIN: the current PIN value must be the undefined value!
                // If not, it means that the PIN value has been set between the moment the user asked to set a new PIN value and now.
                // The PIN can only be set if the PIN is not already set.
                else if (!prefs.getString(Constants.SETTING_PIN_VALUE, Constants.PIN_UNDEFINED_VALUE).equals(Constants.PIN_UNDEFINED_VALUE)) {
                  Toast.makeText(getApplicationContext(), R.string.pindialog_error_alreadyset, Toast.LENGTH_LONG).show();
                } else {
                  getApplicationContext().getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, MODE_PRIVATE).edit()
                    .putString(Constants.SETTING_PIN_VALUE, confirmPinValue)
                    .putLong(Constants.SETTING_PIN_LAST_UPDATE, (new Date()).getTime())
                    .commit();
                }
                pConfirmPinDialog.dismiss();
              }
              @Override
              public void onPinCancel(final PinDialog dialog) {
              }
            }, getString(R.string.pindialog_title_confirmnew));
            pNewPinDialog.dismiss();
            confirmNewPinDialog.show();
          } else {
            Toast.makeText(getApplicationContext(), R.string.pindialog_error_length, Toast.LENGTH_SHORT).show();
          }
        } catch (NumberFormatException e) {
          Toast.makeText(getApplicationContext(), R.string.pindialog_error_notanumber, Toast.LENGTH_SHORT).show();
        }
      }

      @Override
      public void onPinCancel(final PinDialog dialog) {
      }
    }, getString(R.string.pindialog_title_createnew));
    newPinDialog.show();
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
            .putString(Constants.SETTING_PIN_VALUE, Constants.PIN_UNDEFINED_VALUE)
            .putLong(Constants.SETTING_PIN_LAST_UPDATE, (new Date()).getTime())
            .commit();
        } else {
          Toast.makeText(getApplicationContext(), "Incorrect PIN.", Toast.LENGTH_SHORT).show();
        }
      }

      @Override
      public void onPinCancel(final PinDialog dialog) {
      }
    });
    removePinDialog.show();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (checkInit()) {
      refreshPinDisplays(getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, MODE_PRIVATE));
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

  public void deleteNetworkDB(View view) {
    final File datadir = new File(getFilesDir(), Constants.ECLAIR_DATADIR);
    final File networkDB = new File(datadir, "network.sqlite");
    if (networkDB.delete()) {
      Toast.makeText(getApplicationContext(), "Successfully deleted network DB", Toast.LENGTH_SHORT).show();
    }
  }
}
