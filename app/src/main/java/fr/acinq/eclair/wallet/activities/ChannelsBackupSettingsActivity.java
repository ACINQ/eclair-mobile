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
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.Executors;

import fr.acinq.eclair.channel.ChannelPersisted;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityChannelsBackupSettingsBinding;
import fr.acinq.eclair.wallet.databinding.ToolbarBinding;
import fr.acinq.eclair.wallet.utils.BackupHelper;
import fr.acinq.eclair.wallet.utils.EclairException;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class ChannelsBackupSettingsActivity extends ChannelsBackupBaseActivity {

  private final Logger log = LoggerFactory.getLogger(ChannelsBackupSettingsActivity.class);
  private ActivityChannelsBackupSettingsBinding mBinding;
  private Dialog gdriveBackupDetailsDialog;

  @SuppressLint("ClickableViewAccessibility")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_channels_backup_settings);

    setSupportActionBar(mBinding.customToolbar.toolbar);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    gdriveBackupDetailsDialog = getCustomDialog(R.string.backup_about).setPositiveButton(R.string.btn_ok, null).create();
    mBinding.setGoogleDriveAvailable(BackupHelper.GoogleDrive.isGDriveAvailable(getApplicationContext()));

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
          GoogleSignIn.getClient(getApplicationContext(), BackupHelper.GoogleDrive.getGoogleSigninOptions()).revokeAccess()
            .addOnCompleteListener(aVoid -> {
              applyGdriveAccessDenied();
              mBinding.setRequestingGDriveAccess(false);
            });
        } else {
          Executors.newSingleThreadExecutor().execute(this::requestGDriveAccess);
        }
      }
      return true; // consumes touch event
    });
    mBinding.gdriveHelp.setOnClickListener(v -> gdriveBackupDetailsDialog.show());
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (checkInit()) {
      checkGDriveAccess();
      checkLocalAccess();
    }
  }

  private void checkLocalAccess() {
    if (app.seedHash != null && BackupHelper.Local.hasLocalAccess(getApplicationContext())) {
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
        final GoogleSignInAccount signInAccount = BackupHelper.GoogleDrive.getSigninAccount(getApplicationContext());
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
    BackupHelper.GoogleDrive.disableGDriveBackup(getApplicationContext());
    mBinding.gdriveBackupStatus.setVisibility(View.GONE);
    mBinding.requestGdriveAccessSwitch.setChecked(false);
    mBinding.setRequestingGDriveAccess(false);
  }

  @Override
  protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == GDRIVE_REQUEST_CODE_SIGN_IN) {
      handleGdriveSigninResult(data);
    }
  }

  private void handleGdriveSigninResult(final Intent data) {
    try {
      final GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class);
      if (account == null) {
        throw new RuntimeException("empty account");
      }
      applyGdriveAccessGranted(account);
    } catch (Exception e) {
      log.error("Google Drive sign-in failed, could not get account: ", e);
      Toast.makeText(this, "Sign-in failed.", Toast.LENGTH_SHORT).show();
      applyGdriveAccessDenied();
    }
  }

  protected void applyGdriveAccessGranted(final GoogleSignInAccount signInAccount) {
    super.applyGdriveAccessGranted(signInAccount);
    new Thread() {
      @Override
      public void run() {
        if (app != null && !BackupHelper.GoogleDrive.isGDriveEnabled(getApplicationContext())) {
          // access is explicitly granted from a revoked state
          app.system.eventStream().publish(ChannelPersisted.apply(null, null, null, null));
        }
        BackupHelper.GoogleDrive.listBackups(Executors.newSingleThreadExecutor(), mDrive, WalletUtils.getEclairBackupFileName(app.seedHash.get()))
          .addOnSuccessListener(filesList -> runOnUiThread(() -> {
            final com.google.api.services.drive.model.File backup = BackupHelper.GoogleDrive.filterBestBackup(filesList);
            if (backup == null) {
              mBinding.gdriveBackupStatus.setText(getString(R.string.backupsettings_drive_state_nobackup, signInAccount.getEmail()));
            } else {
              mBinding.gdriveBackupStatus.setText(getString(R.string.backupsettings_drive_state, signInAccount.getEmail(),
                DateFormat.getDateTimeInstance().format(new Date(backup.getModifiedTime().getValue()))));
            }
            mBinding.gdriveBackupStatus.setVisibility(View.VISIBLE);
            mBinding.requestGdriveAccessSwitch.setChecked(true);
            mBinding.setRequestingGDriveAccess(false);
            BackupHelper.GoogleDrive.enableGDriveBackup(getApplicationContext());
          }))
          .addOnFailureListener(e -> {
            log.error("could not retrieve best backup from gdrive: ", e);
            if (e instanceof ApiException) {
              if (((ApiException) e).getStatusCode() == CommonStatusCodes.SIGN_IN_REQUIRED) {
                GoogleSignIn.getClient(getApplicationContext(), BackupHelper.GoogleDrive.getGoogleSigninOptions()).revokeAccess();
              }
            }
            if (e instanceof UserRecoverableAuthException) {
              GoogleSignIn.getClient(getApplicationContext(), BackupHelper.GoogleDrive.getGoogleSigninOptions()).revokeAccess();
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
      final File found = BackupHelper.Local.getBackupFile(WalletUtils.getEclairBackupFileName(app.seedHash.get()));
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
