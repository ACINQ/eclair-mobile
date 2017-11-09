package fr.acinq.eclair.wallet.activities;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

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

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_preferences);
    getFragmentManager().beginTransaction()
      .replace(R.id.preference_fragment_placeholder, new PreferencesFragment())
      .commit();

    mPinSwitchWrapper = findViewById(R.id.preference_pin_switch_wrapper);
    mPinSwitch = findViewById(R.id.preference_pin_switch);
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
  }

  /**
   * Refresh the Switch and the last update TextView with the current PIN preferences values.
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
   * Opens a Dialog window to set the PIN to a new value.
   */
  private void setNewPinValue() {
    final PinDialog newPinDialog = new PinDialog(PreferencesActivity.this, R.style.CustomAlertDialog, new PinDialog.PinDialogCallback() {
      @SuppressLint("ApplySharedPref")
      @Override
      public void onPinConfirm(final PinDialog dialog, final String pinValue) {
        try {
          if ((pinValue.length() == Constants.PIN_LENGTH)) {
            Integer.parseInt(pinValue); // check that the pin is a digit
            // 2nd check before disabling
            if (isPinCorrect(Constants.PIN_UNDEFINED_VALUE, dialog)) {
              getApplicationContext().getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, MODE_PRIVATE).edit()
                .putString(Constants.SETTING_PIN_VALUE, pinValue)
                .putLong(Constants.SETTING_PIN_LAST_UPDATE, (new Date()).getTime())
                .commit();
            } else {
              // The PIN value has been set between the moment the user asked to set a PIN value and now
              Toast.makeText(getApplicationContext(), "The PIN has already been set.", Toast.LENGTH_SHORT).show();
            }
          } else {
            Toast.makeText(getApplicationContext(), "The PIN must be a " + Constants.PIN_LENGTH + " digits number.", Toast.LENGTH_SHORT).show();
          }
        } catch (NumberFormatException e) {
          Toast.makeText(getApplicationContext(), "The PIN must be a number.", Toast.LENGTH_SHORT).show();
        }
      }

      @Override
      public void onPinCancel(final PinDialog dialog) {
      }
    }, getString(R.string.pindialog_title_createnew));
    newPinDialog.show();
  }

  /**
   * Opens a Dialog window to remove the PIN if the input is correct
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
    refreshPinDisplays(getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, MODE_PRIVATE));
    getApplicationContext().getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, MODE_PRIVATE)
      .registerOnSharedPreferenceChangeListener(securityPrefsListener);
  }

  @Override
  protected void onPause() {
    super.onPause();
    getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, MODE_PRIVATE)
      .unregisterOnSharedPreferenceChangeListener(securityPrefsListener);
  }
}
