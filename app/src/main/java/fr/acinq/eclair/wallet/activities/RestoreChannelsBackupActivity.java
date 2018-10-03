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

import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.WorkerThread;
import android.view.View;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.metadata.CustomPropertyKey;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.work.ExistingWorkPolicy;
import androidx.work.WorkManager;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityRestoreChannelsBackupBinding;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.EncryptedBackup;
import fr.acinq.eclair.wallet.utils.EncryptedData;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class RestoreChannelsBackupActivity extends GoogleDriveBaseActivity {

  private final Logger log = LoggerFactory.getLogger(RestoreChannelsBackupActivity.class);

  private ActivityRestoreChannelsBackupBinding mBinding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_restore_channels_backup);
    mBinding.setRestoreStep(Constants.RESTORE_BACKUP_INIT);
  }

  public void requestAccess(final View view) {
    final GoogleSignInAccount signInAccount = getSigninAccount(getApplicationContext());
    if (signInAccount == null) {
      final GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, getGoogleSigninOptions());
      startActivityForResult(googleSignInClient.getSignInIntent(), REQUEST_CODE_SIGN_IN);
    } else {
      final GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, getGoogleSigninOptions());
      googleSignInClient.revokeAccess()
        .addOnSuccessListener(aVoid -> initOrSignInGoogleDrive())
        .addOnFailureListener(e -> {
          log.error("could not revoke access to drive", e);
        });
    }
  }

  public void skipRestore(final View view) {
    getCustomDialog(R.string.restorechannels_skip_backup_confirmation)
      .setPositiveButton(R.string.btn_confirm, (dialog, which) -> {
        PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit()
          .putBoolean(Constants.SETTING_CHANNELS_RESTORE_DONE, true).commit();
        finish();
      })
      .setNegativeButton(R.string.btn_cancel, null)
      .create()
      .show();
  }

  public void backToInit(final View view) {
    mBinding.setRestoreStep(Constants.RESTORE_BACKUP_INIT);
  }

  public void finishRestore(final View view) {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    final GoogleSignInAccount signInAccount = getSigninAccount(getApplicationContext());
    final boolean hasDriveAccess = signInAccount != null && !signInAccount.isExpired();
    prefs.edit()
      .putBoolean(Constants.SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_ENABLED, hasDriveAccess)
      .putBoolean(Constants.SETTING_CHANNELS_RESTORE_DONE, true).commit();
    WorkManager.getInstance()
      .beginUniqueWork("ChannelsBackup", ExistingWorkPolicy.REPLACE,
        WalletUtils.generateBackupRequest(app.seedHash.get(), app.backupKey_v2.get()))
      .enqueue();
    this.finish();
  }

  @Override
  void onDriveClientReady(final GoogleSignInAccount signInAccount) {
    applyAccessGranted(signInAccount);
    new Thread() {
      @Override
      public void run() {
        getDriveClient().requestSync()
          .continueWithTask(aVoid -> retrieveEclairBackupTask())
          .addOnSuccessListener(metadataBuffer -> {
            if (metadataBuffer.getCount() == 0) {
              log.info("backup file could not be found in drive");
              runOnUiThread(() -> mBinding.setRestoreStep(Constants.RESTORE_BACKUP_NO_BACKUP_FOUND));
            } else {
              final Metadata metadata = metadataBuffer.get(0);
              final String remoteDeviceId = metadata.getCustomProperties().get(
                new CustomPropertyKey(Constants.BACKUP_META_DEVICE_ID, CustomPropertyKey.PUBLIC));
              final String deviceId = WalletUtils.getDeviceId(getApplicationContext());
              if (remoteDeviceId == null || deviceId.equals(remoteDeviceId)) {
                restoreBackup(metadata.getDriveId().asDriveFile());
              } else {
                log.info("remote backup device id is different from current device id");
                runOnUiThread(() -> mBinding.setRestoreStep(Constants.RESTORE_BACKUP_DEVICE_ORIGIN_CONFLICT));
              }
            }
          })
          .addOnFailureListener(e -> {
            log.error("could not sync app folder", e);
            mBinding.setRestoreStep(Constants.RESTORE_BACKUP_SYNC_RATELIMIT);
            new Handler().postDelayed(() -> backToInit(null), 1700);
          });
      }
    }.start();
  }

  @WorkerThread
  private void restoreBackup(final DriveFile file) {
    getDriveResourceClient().openFile(file, DriveFile.MODE_READ_ONLY)
      .addOnSuccessListener(driveFileContents -> {
        try {
          WalletUtils.getChainDatadir(getApplicationContext()).mkdirs();

          // decrypt file content
          final EncryptedBackup encryptedContent = EncryptedBackup.read(getBytesFromDriveContents(driveFileContents));

          // decrypt and write backup
          Files.write(encryptedContent.decrypt(EncryptedData.secretKeyFromBinaryKey(
            // backward compatibility code for v0.3.6-TESTNET which uses backup version 1
            EncryptedBackup.BACKUP_VERSION_1 == encryptedContent.getVersion() ? app.backupKey_v1.get() : app.backupKey_v2.get())),
            WalletUtils.getEclairDBFile(getApplicationContext()));

          // celebrate
          runOnUiThread(() -> {
            mBinding.setRestoreStep(Constants.RESTORE_BACKUP_SUCCESS);
            new Handler().postDelayed(() -> finishRestore(null), 1700);
          });
        } catch (Throwable t) {
          log.error("could not copy remote file backup to datadir", t);
          runOnUiThread(() -> {
            mBinding.setRestoreStep(Constants.RESTORE_BACKUP_FAILURE);
            new Handler().postDelayed(() -> backToInit(null), 1700);
          });
        }
        getDriveResourceClient().discardContents(driveFileContents);
      })
      .addOnFailureListener(e -> runOnUiThread(() -> {
        log.error("backup file could not be restored from drive", e);
        mBinding.setRestoreStep(Constants.RESTORE_BACKUP_FAILURE);
        new Handler().postDelayed(() -> backToInit(null), 1700);
      }));
  }

  public void restoreIfAppIdConflict(final View view) {
    final GoogleSignInAccount signInAccount = getSigninAccount(getApplicationContext());
    if (signInAccount == null) {
      requestAccess(null);
    } else {
      mBinding.setRestoreStep(Constants.RESTORE_BACKUP_SEARCHING);
      new Thread() {
        @Override
        public void run() {
          retrieveEclairBackupTask()
            .addOnSuccessListener(metadataBuffer -> {
              if (metadataBuffer.getCount() == 0) {
                runOnUiThread(() -> {
                  log.info("backup file could not be found in drive");
                  mBinding.setRestoreStep(Constants.RESTORE_BACKUP_NO_BACKUP_FOUND);
                });
              } else {
                restoreBackup(metadataBuffer.get(0).getDriveId().asDriveFile());
              }
            })
            .addOnFailureListener(e -> {
              log.error("could not sync app folder", e);
              mBinding.setRestoreStep(Constants.RESTORE_BACKUP_SYNC_RATELIMIT);
              new Handler().postDelayed(() -> backToInit(null), 1700);
            });
        }
      }.start();
    }
  }

  @Override
  void applyAccessDenied() {
    mBinding.setRestoreStep(Constants.RESTORE_BACKUP_ERROR_PERMISSIONS);
    new Handler().postDelayed(() -> backToInit(null), 2000);
  }

  @Override
  void applyAccessGranted(GoogleSignInAccount signIn) {
    mBinding.setRestoreStep(Constants.RESTORE_BACKUP_SEARCHING);
  }
}
