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

import android.app.Dialog;
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.ContextThemeWrapper;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.view.View;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;

import java.text.DateFormat;

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityChannelsBackupSettingsBinding;
import fr.acinq.eclair.wallet.utils.Constants;

public class ChannelsBackupSettingsActivity extends GoogleDriveBaseActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

  private static final String TAG = ChannelsBackupSettingsActivity.class.getSimpleName();
  private ActivityChannelsBackupSettingsBinding mBinding;
  private Dialog backupAbout;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_channels_backup_settings);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);

    final AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.CustomAlertDialog));
    builder.setMessage(Html.fromHtml(getString(R.string.setupbackup_about)));
    backupAbout = builder.create();

    final int connectionResult = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getApplicationContext());
    if (connectionResult != ConnectionResult.SUCCESS) {
      Log.i(TAG, "Google play services are not available (code " + connectionResult + ")");
      mBinding.setGoogleDriveAvailable(false);
    } else {
      mBinding.setGoogleDriveAvailable(true);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    refreshSwitchState(PreferenceManager.getDefaultSharedPreferences(this));
    PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
    checkAccess();
  }

  @Override
  public void onPause() {
    super.onPause();
    PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
  }

  public void switchAccess(final View view) {
    if (mBinding.switchButton.isChecked()) {
      revokeAccess();
    } else {
      grantAccess();
    }
  }

  private void grantAccess() {
    new Thread() {
      @Override
      public void run() {
        initOrSignInGoogleDrive();
      }
    }.start();
  }

  public void showDetails(final View view) {
    if (backupAbout != null) backupAbout.show();
  }

  void applyAccessDenied() {
    Log.i(TAG, "google drive access is denied!");
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    prefs.edit().putBoolean(Constants.SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_ENABLED, false).apply();
    mBinding.setAccessDenied(true);
  }

  void applyAccessGranted(final GoogleSignInAccount signIn) {
    Log.i(TAG, "google drive access is granted!");
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    prefs.edit().putBoolean(Constants.SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_ENABLED, true).apply();
    mBinding.accessAccount.setText(getString(R.string.backup_drive_account, signIn.getEmail()));
    mBinding.setAccessDenied(false);
  }

  @Override
  void onDriveClientReady(final GoogleSignInAccount signInAccount) {
    new Thread() {
      @Override
      public void run() {
        runOnUiThread(() -> applyAccessGranted(signInAccount));
        retrieveEclairBackupTask().addOnSuccessListener(metadataBuffer -> runOnUiThread(() -> {
          if (metadataBuffer.getCount() == 0) {
            mBinding.existingBackupState.setText(getString(R.string.backup_drive_no_backup));
          } else {
            mBinding.existingBackupState.setText(DateFormat.getDateTimeInstance().format(metadataBuffer.get(0).getModifiedDate()));
          }
        })).addOnFailureListener(e -> runOnUiThread(() -> {
          Log.i(TAG, "could not get backup metada", e);
          mBinding.existingBackupState.setText(getString(R.string.backup_drive_no_backup));
          if (e instanceof ApiException) {
            applyAccessDenied();
          }
        }));
      }
    }.start();
  }

  @Override
  public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
    if (Constants.SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_ENABLED.equals(key)) {
      refreshSwitchState(prefs);
    }
  }

  private void refreshSwitchState(final SharedPreferences prefs) {
    final boolean isChannelsBackupEnabled = prefs.getBoolean(Constants.SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_ENABLED, false);
    mBinding.switchButton.setChecked(isChannelsBackupEnabled);
  }
}
