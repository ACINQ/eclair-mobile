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
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.tasks.Task;
import com.google.common.io.Files;

import java.io.File;
import java.util.List;

import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityRestoreChannelsBackupBinding;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class RestoreChannelsBackupActivity extends GoogleDriveBaseActivity {

  private static final String TAG = RestoreChannelsBackupActivity.class.getSimpleName();

  private ActivityRestoreChannelsBackupBinding mBinding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_restore_channels_backup);
    mBinding.setRestoreStep(Constants.RESTORE_BACKUP_INIT);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    switch (requestCode) {
      case REQUEST_CODE_SIGN_IN:
        if (resultCode != RESULT_OK) {
          Log.e(TAG, "Google Drive sign-in failed with code " + resultCode);
          mBinding.setRestoreStep(Constants.RESTORE_BACKUP_ERROR_PERMISSIONS);
          return;
        }

        final Task<GoogleSignInAccount> getAccountTask = GoogleSignIn.getSignedInAccountFromIntent(data);
        if (getAccountTask.isSuccessful()) {
          initializeDriveClient(getAccountTask.getResult());
        } else {
          Log.e(TAG, "Google Drive sign-in failed, could not get account");
          mBinding.setRestoreStep(Constants.RESTORE_BACKUP_ERROR_PERMISSIONS);
        }
        break;
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  public void requestAccess(final View view) {
    initOrSignInGoogleDrive();
  }

  public void backToInit(final View view) {
    mBinding.setRestoreStep(Constants.RESTORE_BACKUP_INIT);
  }

  public void finishRestore(final View view) {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    prefs.edit()
      .putBoolean(Constants.SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_ENABLED, true)
      .putBoolean(Constants.SETTING_CHANNELS_RESTORE_DONE, true).commit();
    this.finish();
  }

  @Override
  void onDriveClientReady(final GoogleSignInAccount signInAccount) {
    mBinding.setRestoreStep(Constants.RESTORE_BACKUP_SEARCHING);
    new Thread() {
      @Override
      public void run() {
        retrieveEclairBackupTask().continueWithTask(metadataBufferTask -> {
          final MetadataBuffer metadataBuffer = metadataBufferTask.getResult();
          if (metadataBuffer.getCount() == 0) {
            throw new NoFilesFound("0 file found");
          } else {
            return getDriveResourceClient().openFile(metadataBuffer.get(0).getDriveId().asDriveFile(), DriveFile.MODE_READ_ONLY);
          }
        }).addOnSuccessListener(driveFileContents -> {
          try {
            WalletUtils.getChainDatadir(getApplicationContext()).mkdirs();
            Files.write(getBytesFromDriveContents(driveFileContents), WalletUtils.getEclairDBFile(getApplicationContext()));
            runOnUiThread(() -> {
              mBinding.setRestoreStep(Constants.RESTORE_BACKUP_SUCCESS);
              new Handler().postDelayed(() -> finishRestore(null), 1700);
            });
          } catch (Throwable throwable) {
            Log.e(TAG, "could not copy Drive file backup to datadir", throwable);
            runOnUiThread(() -> {
              mBinding.setRestoreStep(Constants.RESTORE_BACKUP_FAILURE);
              new Handler().postDelayed(() -> backToInit(null), 1700);
            });
          }
          getDriveResourceClient().discardContents(driveFileContents);
        }).addOnFailureListener(e -> {
          runOnUiThread(() -> {
            if (e instanceof NoFilesFound) {
              Log.e(TAG, "backup file could not be found in drive", e);
              mBinding.setRestoreStep(Constants.RESTORE_BACKUP_NO_BACKUP_FOUND);
            } else {
              Log.e(TAG, "backup file could not be restored from drive", e);
              mBinding.setRestoreStep(Constants.RESTORE_BACKUP_FAILURE);
              new Handler().postDelayed(() -> backToInit(null), 1700);
            }
          });
        });
      }
    }.start();

  }
}
