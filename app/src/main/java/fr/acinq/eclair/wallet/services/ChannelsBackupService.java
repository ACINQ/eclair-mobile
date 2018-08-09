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

package fr.acinq.eclair.wallet.services;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.tasks.Task;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.activities.GoogleDriveBaseActivity;
import fr.acinq.eclair.wallet.utils.EncryptedBackup;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class ChannelsBackupService extends JobIntentService {

  private static final String TAG = ChannelsBackupService.class.getSimpleName();
  public final static String SEED_HASH_EXTRA = BuildConfig.APPLICATION_ID + ".SEED_HASH2";

  @Override
  protected void onHandleWork(@NonNull Intent intent) {
    Log.i(TAG, "Channel backup requested");
    if (intent.hasExtra(SEED_HASH_EXTRA)) {
      final String seedHash = intent.getStringExtra(SEED_HASH_EXTRA);
      Log.i(TAG, "Saving eclair DB in Drivemm intent seed hash=" + seedHash );
      final Context context = getApplicationContext();
      final GoogleSignInAccount signInAccount = GoogleDriveBaseActivity.getSigninAccount(context);
      if (signInAccount != null) {
        final String backupFileName = WalletUtils.getEclairBackupFileName(seedHash);
        final DriveResourceClient driveResourceClient = Drive.getDriveResourceClient(context, signInAccount);
        final Task<DriveFolder> appFolderTask = driveResourceClient.getAppFolder();
        appFolderTask
          .continueWithTask(t -> {
            return GoogleDriveBaseActivity.retrieveEclairBackupTask(appFolderTask, driveResourceClient, backupFileName);
          })
          .addOnSuccessListener(metadataBuffer -> {
            if (metadataBuffer.getCount() == 0) {
              createBackup(context, driveResourceClient, appFolderTask, backupFileName)
                .addOnSuccessListener(v -> {
                  Log.i(TAG, "successfully created channels backup");
                  Toast.makeText(context, "Backup was created!", Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                  Toast.makeText(context, "Could not create backup!", Toast.LENGTH_LONG).show();
                  Log.e(TAG, "could not create backup", e);
                });
            } else {
              Log.i(TAG, "update");
              updateBackup(context, driveResourceClient, metadataBuffer.get(0).getDriveId().asDriveFile())
                .addOnSuccessListener(v -> {
                  Log.i(TAG, "successfully updated channels backup");
                  Toast.makeText(context, "Backup was updated!", Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                  Toast.makeText(context, "Could not update backup!", Toast.LENGTH_LONG).show();
                  Log.e(TAG, "could not update backup", e);
                });
            }
          })
          .addOnFailureListener(e -> {
            Toast.makeText(context, "Could not save backup!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Unable to save backup file", e);
          });
      } else {
        Log.e(TAG, "could not save channels backup, permission required...");
      }
    }
  }

  private Task<DriveFile> createBackup(final Context context, final DriveResourceClient driveResourceClient,
                                             Task<DriveFolder> appFolderTask, final String backupFileName) {
    return driveResourceClient.createContents().continueWithTask(contentsTask -> {
      final File eclairDBFile = WalletUtils.getEclairDBFile(context);
      final DriveContents contents = contentsTask.getResult();

      // encrypt backup
      final EncryptedBackup backup = EncryptedBackup.encrypt(
        Files.toByteArray(eclairDBFile), "1234", EncryptedBackup.BACKUP_VERSION_1);

      // write encrypted backup as file content
      final InputStream i = new ByteArrayInputStream(backup.write());
      ByteStreams.copy(i, contents.getOutputStream());
      i.close();

      final MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
        .setTitle(backupFileName)
        .setMimeType("application/octet-stream")
        .build();

      return driveResourceClient.createFile(appFolderTask.getResult(), changeSet, contents);
    });
  }

  private Task<Void> updateBackup(final Context context, final DriveResourceClient driveResourceClient, final DriveFile driveFile) {
    return driveResourceClient.openFile(driveFile, DriveFile.MODE_WRITE_ONLY).continueWithTask(contentsTask -> {
      final File eclairDBFile = WalletUtils.getEclairDBFile(context);
      final DriveContents contents = contentsTask.getResult();

      // encrypt backup
      final EncryptedBackup backup = EncryptedBackup.encrypt(
        Files.toByteArray(eclairDBFile), "1234", EncryptedBackup.BACKUP_VERSION_1);

      // write encrypted backup as file content
      final InputStream i = new ByteArrayInputStream(backup.write());
      ByteStreams.copy(i, contents.getOutputStream());
      i.close();

      return driveResourceClient.commitContents(contents, null);
    });
  }
}
