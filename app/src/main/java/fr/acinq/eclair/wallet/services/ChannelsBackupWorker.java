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
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import androidx.work.Worker;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.activities.GoogleDriveBaseActivity;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.EncryptedBackup;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class ChannelsBackupWorker extends Worker {

  public final static String BACKUP_NAME_INPUT = BuildConfig.APPLICATION_ID + ".BACKUP_NAME";
  private static final String TAG = ChannelsBackupWorker.class.getSimpleName();

  @NonNull
  @Override
  public Result doWork() {

    final String backupFileName = getInputData().getString(BACKUP_NAME_INPUT);
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    if (!prefs.getBoolean(Constants.SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_ENABLED, false) || backupFileName == null) {
      Log.i(TAG, "ignored channels backup request because feature is disabled.");
      return Result.SUCCESS;
    }

    final Context context = getApplicationContext();
    final GoogleSignInAccount signInAccount = GoogleDriveBaseActivity.getSigninAccount(context);

    // --- check authorization
    if (signInAccount == null) {
      Log.i(TAG, "account is not signed in");
      return Result.FAILURE;
    }

    Log.i(TAG, "saving backup with file name=" + backupFileName);
    final DriveResourceClient driveResourceClient = Drive.getDriveResourceClient(context, signInAccount);

    final Task<DriveFolder> appFolderTask = driveResourceClient.getAppFolder();
    final Task<MetadataBuffer> metadataBufferTask = appFolderTask
      .continueWithTask(t -> GoogleDriveBaseActivity.retrieveEclairBackupTask(appFolderTask, driveResourceClient, backupFileName));
    try {
      final MetadataBuffer buffer = Tasks.await(metadataBufferTask, 60, TimeUnit.SECONDS);

      if (buffer.getCount() == 0) {
        Tasks.await(createBackup(context, driveResourceClient, appFolderTask, backupFileName)
          .addOnSuccessListener(aVoid -> {
            Log.i(TAG, "successfully created channels backup");
            Toast.makeText(context, "Backup was created!", Toast.LENGTH_SHORT).show();
          })
          .addOnFailureListener(e -> {
            Toast.makeText(context, "Could not create backup!", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "could not create backup", e);
          }), 60, TimeUnit.SECONDS);
      } else {
        Tasks.await(updateBackup(context, driveResourceClient, buffer.get(0).getDriveId().asDriveFile())
          .addOnSuccessListener(v -> {
            Log.i(TAG, "successfully updated channels backup");
            Toast.makeText(context, "Backup was updated!", Toast.LENGTH_SHORT).show();
          })
          .addOnFailureListener(e -> {
            Toast.makeText(context, "Could not update backup!", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "could not update backup", e);
          }), 60, TimeUnit.SECONDS);
      }

      return Result.SUCCESS;
    } catch (Exception e) {
      Log.e(TAG, "failed to retrieve backup metadata", e);
      return Result.FAILURE;
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
