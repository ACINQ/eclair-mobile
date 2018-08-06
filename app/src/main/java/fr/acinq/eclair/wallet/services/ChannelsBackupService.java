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

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
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

import java.io.File;
import java.io.InputStream;

import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.activities.GoogleDriveBaseActivity;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class ChannelsBackupService extends IntentService {

  private static final String TAG = ChannelsBackupService.class.getSimpleName();
  public final static String SEED_HASH_EXTRA = BuildConfig.APPLICATION_ID + ".SEED_HASH";


  public ChannelsBackupService() {
    super("ChannelsBackupService");
  }

  @Override
  protected void onHandleIntent(@Nullable Intent intent) {
    Log.i(TAG, "Channel backup requested");
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    if (prefs.getBoolean(Constants.SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_ENABLED, false) && intent.hasExtra(SEED_HASH_EXTRA)) {

      final GoogleSignInAccount signInAccount = GoogleDriveBaseActivity.getSigninAccount(getApplicationContext());
      if (signInAccount != null) {
        saveEclairDBInDrive(Drive.getDriveResourceClient(getApplicationContext(), signInAccount), getApplicationContext(),
          WalletUtils.getEclairBackupFileName(intent.getStringExtra(SEED_HASH_EXTRA)));
      } else {
        Log.e(TAG, "could not save channels backup, permission required...");
      }
    } else {
      Log.d(TAG, "backup event ignored because channel backup is disabled in settings");
    }
  }

  public void saveEclairDBInDrive(final DriveResourceClient driveResourceClient, final Context context, final String backupFileName) {
    Log.i(TAG, "Saving eclair DB in Drive");
    final Task<DriveFolder> appFolderTask = driveResourceClient.getAppFolder();
    try {
      appFolderTask.continueWithTask(t -> {
        return GoogleDriveBaseActivity.retrieveEclairBackupTask(appFolderTask, driveResourceClient, backupFileName);
      }).addOnSuccessListener(metadataBuffer -> {
        if (metadataBuffer.getCount() == 0) {
          driveResourceClient.createContents().continueWithTask(contentsTask -> {
            final File eclairDBFile = WalletUtils.getEclairDBFile(context);
            final DriveContents contents = contentsTask.getResult();

            final InputStream i = Files.asByteSource(eclairDBFile).openStream();
            ByteStreams.copy(i, contents.getOutputStream());
            i.close();

            final MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
              .setTitle(backupFileName)
              .setMimeType("application/octet-stream")
              .build();

            return driveResourceClient.createFile(appFolderTask.getResult(), changeSet, contents);
          }).addOnSuccessListener(v -> {
            Toast.makeText(context, "Backup was created!", Toast.LENGTH_LONG).show();
          }).addOnFailureListener(e -> {
            Toast.makeText(context, "Could not create backup!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "could not create backup", e);
          });
        } else {
          driveResourceClient.openFile(metadataBuffer.get(0).getDriveId().asDriveFile(), DriveFile.MODE_WRITE_ONLY).continueWithTask(contentsTask -> {
            final File eclairDBFile = WalletUtils.getEclairDBFile(context);
            final DriveContents contents = contentsTask.getResult();

            final InputStream i = Files.asByteSource(eclairDBFile).openStream();
            ByteStreams.copy(i, contents.getOutputStream());
            i.close();

            return driveResourceClient.commitContents(contents, null);
          }).addOnSuccessListener(v -> {
            Toast.makeText(context, "Backup was updated!", Toast.LENGTH_LONG).show();
          }).addOnFailureListener(e -> {
            Toast.makeText(context, "Could not update backup!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "could not update backup", e);
          });
        }
      }).addOnFailureListener(e -> {
        Toast.makeText(context, "Could not save backup!", Toast.LENGTH_LONG).show();
        Log.e(TAG, "Unable to save backup file", e);
      });
    } catch (Throwable t) {
      Toast.makeText(context, "Could not save backup!", Toast.LENGTH_LONG).show();
      Log.e(TAG, "Unable to save backup file", t);
    }
  }
}
