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
import android.util.Log;
import android.view.View;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.common.io.Files;

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityRestoreChannelsBackupBinding;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.EncryptedBackup;
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

  public void requestAccess(final View view) {

    final GoogleSignInAccount signInAccount = getSigninAccount(getApplicationContext());
    if (signInAccount == null) {
      final GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, getGoogleSigninOptions());
      startActivityForResult(googleSignInClient.getSignInIntent(), REQUEST_CODE_SIGN_IN);
    } else {
      final GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(getApplicationContext(), getGoogleSigninOptions());
      googleSignInClient.revokeAccess()
        .addOnSuccessListener(aVoid -> initOrSignInGoogleDrive())
        .addOnFailureListener(e -> {
          Log.e(TAG, "could not revoke access to drive", e);
        });
    }

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
    applyAccessGranted(signInAccount);


    new Thread() {
      @Override
      public void run() {


        getDriveClient().requestSync()
          .addOnSuccessListener(aVoid -> {
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

                // decrypt file content
                final EncryptedBackup encryptedContent = EncryptedBackup.read(getBytesFromDriveContents(driveFileContents));

                // decrypt and write backup
                Files.write(encryptedContent.decrypt("1234"), WalletUtils.getEclairDBFile(getApplicationContext()));

                // celebrate
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
                  Log.e(TAG, "backup file could not be found in drive");
                  mBinding.setRestoreStep(Constants.RESTORE_BACKUP_NO_BACKUP_FOUND);
                } else {
                  Log.e(TAG, "backup file could not be restored from drive", e);
                  mBinding.setRestoreStep(Constants.RESTORE_BACKUP_FAILURE);
                  new Handler().postDelayed(() -> backToInit(null), 1700);
                }
              });
            });
          })
          .addOnFailureListener(e -> {
            Log.e(TAG, "could not sync app folder", e);
            mBinding.setRestoreStep(Constants.RESTORE_BACKUP_SYNC_RATELIMIT);
            new Handler().postDelayed(() -> backToInit(null), 1700);
          });
      }
    }.start();

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
