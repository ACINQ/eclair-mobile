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
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.tasks.Task;

import java.text.DateFormat;
import java.util.HashSet;
import java.util.Set;

import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityEnableChannelsBackupBinding;
import fr.acinq.eclair.wallet.utils.Constants;

public class EnableChannelsBackupActivity extends GoogleDriveBaseActivity {

  private static final String TAG = EnableChannelsBackupActivity.class.getSimpleName();
  public static final String FROM_STARTUP = BuildConfig.APPLICATION_ID + ".FROM_STARTUP";
  private ActivityEnableChannelsBackupBinding mBinding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_enable_channels_backup);

    final Intent intent = getIntent();
    final boolean fromStartup = intent.getBooleanExtra(FROM_STARTUP, false);
    if (fromStartup) {
      mBinding.closeButton.setText(getString(R.string.backup_drive_nevermind));
      mBinding.closeButton.setOnClickListener(v -> {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        prefs.edit().putBoolean(Constants.SETTING_CHANNELS_BACKUP_SEEN_ONCE, true).apply();
        finish();
      });
    } else {
      mBinding.closeButton.setOnClickListener(v -> finish());
    }

    final int connectionResult = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getApplicationContext());
    if (connectionResult != ConnectionResult.SUCCESS) {
      Log.i(TAG, "Google play services are not available (code " + connectionResult + ")");
      mBinding.setGoogleDriveAvailable(false);
    } else {
      mBinding.setGoogleDriveAvailable(true);
    }
  }

  protected void checkAccess() {
    new Thread() {
      @Override
      public void run() {
        final GoogleSignInAccount signInAccount = getSigninAccount(getApplicationContext());
        if (signInAccount != null) {
          initializeDriveClient(signInAccount);
        } else {
          Log.i(TAG, "google drive signin account is null");
          runOnUiThread(() -> applyAccessDenied());
        }
      }
    }.start();
  }

  @Override
  protected void onResume() {
    super.onResume();
    checkAccess();
  }

  @Override
  protected void onPause() {
    super.onPause();
  }

  public void grantAccess(final View view) {
    new Thread() {
      @Override
      public void run() {
        initOrSignInGoogleDrive();
      }
    }.start();
  }

  public void revokeAccess(final View view) {
    new Thread() {
      @Override
      public void run() {
        final GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(getApplicationContext(), getGoogleSigninOptions());
        // force sign out to display account picker
        googleSignInClient.revokeAccess()
          .addOnSuccessListener(aVoid -> runOnUiThread(() -> applyAccessDenied()))
          .addOnFailureListener(e -> {
          Log.e(TAG, "could not revoke access to drive", e);
          checkAccess();
        });
      }
    }.start();
  }

  private void applyAccessDenied() {
    Log.i(TAG, "google drive access is denied!");
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    prefs.edit().putBoolean(Constants.SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_ENABLED, false).apply();
    mBinding.setAccessDenied(true);
  }

  private void applyAccessGranted(final GoogleSignInAccount signIn) {
    Log.i(TAG, "google drive access is granted!");
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    prefs.edit().putBoolean(Constants.SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_ENABLED, true).apply();
    mBinding.accessAccount.setText(getString(R.string.backup_drive_access_account, signIn.getEmail()));
    mBinding.setAccessDenied(false);
  }

  public void showDetails(final View view) {
    mBinding.setShowDetails(true);
  }

  @Override
  protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    switch (requestCode) {
      case REQUEST_CODE_SIGN_IN:
        if (resultCode != RESULT_OK) {
          Log.i(TAG, "Google Drive sign-in failed with code " + resultCode);
          applyAccessDenied();
          return;
        }
        final Task<GoogleSignInAccount> getAccountTask = GoogleSignIn.getSignedInAccountFromIntent(data);
        if (getAccountTask.isSuccessful()) {
          initializeDriveClient(getAccountTask.getResult());
        } else {
          Log.i(TAG, "Google Drive sign-in failed, could not get account");
          applyAccessDenied();
        }
        break;
    }
    super.onActivityResult(requestCode, resultCode, data);
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
            mBinding.existingBackupState.setText(getString(R.string.backup_drive_has_backup,
              DateFormat.getDateTimeInstance().format(metadataBuffer.get(0).getModifiedDate())));
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

  public void close(final View view) {
    finish();
  }
}
