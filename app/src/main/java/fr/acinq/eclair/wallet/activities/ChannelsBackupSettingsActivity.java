/*
 * Copyright 2019 ACINQ SAS
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
import android.app.Dialog;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.MotionEvent;
import android.view.View;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityChannelsBackupSettingsBinding;
import fr.acinq.eclair.wallet.services.BackupUtils;
import fr.acinq.eclair.wallet.utils.EclairException;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.DateFormat;

public class ChannelsBackupSettingsActivity extends ChannelsBackupBaseActivity {

  private final Logger log = LoggerFactory.getLogger(ChannelsBackupSettingsActivity.class);
  private ActivityChannelsBackupSettingsBinding mBinding;
  private Dialog gdriveBackupDetailsDialog;

  @SuppressLint("ClickableViewAccessibility")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_channels_backup_settings);

    setSupportActionBar((Toolbar) mBinding.customToolbar);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    gdriveBackupDetailsDialog = getCustomDialog(R.string.backup_about).setPositiveButton(R.string.btn_ok, null).create();
    mBinding.setGoogleDriveAvailable(BackupUtils.GoogleDrive.isGDriveAvailable(getApplicationContext()));

    mBinding.requestLocalAccessSwitch.setOnTouchListener((v, event) -> {
      if (event.getAction() == MotionEvent.ACTION_DOWN) {
        requestLocalAccessOrApply();
      }
      return true; // consumes touch event
    });

    mBinding.requestGdriveAccessSwitch.setOnTouchListener((v, event) -> {
      if (event.getAction() == MotionEvent.ACTION_DOWN && !mBinding.getRequestingGDriveAccess()) {
        mBinding.setRequestingGDriveAccess(true);
        mBinding.gdriveBackupStatus.setVisibility(View.GONE);
        if (mBinding.requestGdriveAccessSwitch.isChecked()) {
          log.info("revoking access to gdrive");
          GoogleSignIn.getClient(getApplicationContext(), getGoogleSigninOptions()).revokeAccess()
            .addOnCompleteListener(aVoid -> {
              applyGdriveAccessDenied();
              mBinding.setRequestingGDriveAccess(false);
            });
        } else {
          requestGDriveAccess();
        }
      }
      return true; // consumes touch event
    });
    mBinding.gdriveHelp.setOnClickListener(v -> gdriveBackupDetailsDialog.show());
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (checkInit()) {
      checkGDriveAccess();
      checkLocalAccess();
    }
  }

  private void checkLocalAccess() {
    if (app.seedHash != null && BackupUtils.Local.hasLocalAccess(getApplicationContext())) {
      applyLocalAccessGranted();
    } else {
      applyLocalAccessDenied();
    }
  }

  private void checkGDriveAccess() {
    mBinding.setRequestingGDriveAccess(true);
    new Thread() {
      @Override
      public void run() {
        final GoogleSignInAccount signInAccount = BackupUtils.GoogleDrive.getSigninAccount(getApplicationContext());
        if (signInAccount != null) {
          runOnUiThread(() -> applyGdriveAccessGranted(signInAccount));
        } else {
          runOnUiThread(() -> applyGdriveAccessDenied());
        }
      }
    }.start();
  }

  protected void applyGdriveAccessDenied() {
    super.applyGdriveAccessDenied();
    BackupUtils.GoogleDrive.disableGDriveBackup(getApplicationContext());
    mBinding.gdriveBackupStatus.setVisibility(View.GONE);
    mBinding.requestGdriveAccessSwitch.setChecked(false);
    mBinding.setRequestingGDriveAccess(false);
  }

  protected void applyGdriveAccessGranted(final GoogleSignInAccount signInAccount) {
    super.applyGdriveAccessGranted(signInAccount);
    new Thread() {
      @Override
      public void run() {
        retrieveEclairBackupTask().addOnSuccessListener(metadataBuffer -> runOnUiThread(() -> {
          mBinding.gdriveBackupStatus.setVisibility(View.VISIBLE);
          if (metadataBuffer.getCount() == 0) {
            mBinding.gdriveBackupStatus.setText(getString(R.string.backupsettings_drive_state_nobackup, signInAccount.getEmail()));
          } else {
            mBinding.gdriveBackupStatus.setText(getString(R.string.backupsettings_drive_state, signInAccount.getEmail(), DateFormat.getDateTimeInstance().format(metadataBuffer.get(0).getModifiedDate())));
          }
          mBinding.requestGdriveAccessSwitch.setChecked(true);
          mBinding.setRequestingGDriveAccess(false);
          BackupUtils.GoogleDrive.enableGDriveBackup(getApplicationContext());
        })).addOnFailureListener(e -> {
          log.info("could not get backup metadata with cause {}", e.getLocalizedMessage());
          if (e instanceof ApiException) {
            if (((ApiException) e).getStatusCode() == CommonStatusCodes.SIGN_IN_REQUIRED) {
              GoogleSignIn.getClient(getApplicationContext(), getGoogleSigninOptions()).revokeAccess();
            }
          }
          applyGdriveAccessDenied();
        });
      }
    }.start();
  }

  @Override
  protected void applyLocalAccessDenied() {
    super.applyLocalAccessDenied();
    mBinding.localBackupStatus.setVisibility(View.GONE);
    mBinding.setHasLocalAccess(false);
  }

  @Override
  protected void applyLocalAccessGranted() {
    super.applyLocalAccessGranted();
    try {
      final File found = BackupUtils.Local.getBackupFile(WalletUtils.getEclairBackupFileName(app.seedHash.get()));
      if (found.exists()) {
        mBinding.localBackupStatus.setVisibility(View.VISIBLE);
        mBinding.localBackupStatus.setText(getString(R.string.backupsettings_local_status_result, DateFormat.getDateTimeInstance().format(found.lastModified())));
      } else {
        mBinding.localBackupStatus.setVisibility(View.VISIBLE);
        mBinding.localBackupStatus.setText(getString(R.string.backupsettings_local_status_not_found));
      }
      mBinding.setHasLocalAccess(true);
    } catch (EclairException.ExternalStorageUnavailableException e) {
      log.error("could not access storage: ", e);
      applyLocalAccessDenied();
    }
  }
}
