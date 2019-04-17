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

package fr.acinq.eclair.wallet.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.android.gms.drive.*;
import com.google.android.gms.drive.metadata.CustomPropertyKey;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.tozny.crypto.android.AesCbcWithIntegrity;
import fr.acinq.bitcoin.ByteVector32;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.activities.ChannelsBackupBaseActivity;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.EncryptedBackup;
import fr.acinq.eclair.wallet.utils.EncryptedData;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;
import scodec.bits.ByteVector;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Objects;

public class ChannelsBackupWorker extends Worker {
  private final Logger log = LoggerFactory.getLogger(ChannelsBackupWorker.class);
  public final static String BACKUP_NAME_INPUT = BuildConfig.APPLICATION_ID + ".BACKUP_NAME";
  public final static String BACKUP_KEY_INPUT = BuildConfig.APPLICATION_ID + ".BACKUP_KEY_INPUT";

  public ChannelsBackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
    super(context, workerParams);
  }

  @NonNull
  @Override
  public Result doWork() {

    final String backupFileName = getInputData().getString(BACKUP_NAME_INPUT);
    final String key = getInputData().getString(BACKUP_KEY_INPUT);
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

    if (!WalletUtils.getEclairDBFile(getApplicationContext()).exists()) {
      log.info("no eclair db file yet...");
      return Result.success();
    }

    if (backupFileName == null) {
      log.error("backup file name is null, aborting job");
      return Result.failure();
    }

    if (key == null) {
      log.error("backup key is null, aborting job");
      return Result.failure();
    }

    try {
      // 1 - generate encrypted backup file
      final AesCbcWithIntegrity.SecretKeys sk = EncryptedData.secretKeyFromBinaryKey(ByteVector32.apply(ByteVector.view(Hex.decode(key))));
      final byte[] encryptedBackup = getEncryptedBackup(getApplicationContext(), sk);

      // 2 - save to drive
      final boolean shouldBackupToDrive = BackupUtils.GoogleDrive.isGDriveAvailable(getApplicationContext()) && BackupUtils.GoogleDrive.getSigninAccount(getApplicationContext()) != null;
      boolean driveBackupSuccessful = true;
      if (shouldBackupToDrive) {
        driveBackupSuccessful = saveToGoogleDrive(prefs, getApplicationContext(), encryptedBackup, backupFileName);
      }

      // 3 - save to local
      final boolean localBackupSuccessful = saveToLocal(encryptedBackup, backupFileName);

      // 4 - handle result
      if (localBackupSuccessful || (shouldBackupToDrive && driveBackupSuccessful)) {
        return Result.success();
      } else {
        log.info("failing backup worker: local ? {} ; drive ? {}", localBackupSuccessful, driveBackupSuccessful);
        return Result.failure();
      }
    } catch (Throwable t) {
      log.error("error when generating channels backup", t);
      return Result.failure();
    }
  }

  private byte[] getEncryptedBackup(final Context context, final AesCbcWithIntegrity.SecretKeys sk) throws IOException, GeneralSecurityException {
    final File eclairDBFile = WalletUtils.getEclairDBFile(context);
    final EncryptedBackup backup = EncryptedBackup.encrypt(Files.toByteArray(eclairDBFile), sk, EncryptedBackup.BACKUP_VERSION_2);
    return backup.write();
  }
//
//  public static File getLocalBackupDir() {
//    return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), Constants.LOCAL_BACKUP_DIR);
//  }
//
//  public static File getLocalBackup(final Context context, final String backupFileName) {
//    return new File(getLocalBackupDir(), backupFileName);
//  }

  private boolean saveToLocal(final byte[] encryptedBackup, final String backupFileName) {
    try {
      final File backupFile = BackupUtils.Local.getBackupFile(backupFileName);
      Files.write(encryptedBackup, backupFile);
      return true;
    } catch (Throwable t) {
      log.error("failed to save channels backup on local disk", t);
      return false;
    }
  }

//  /**
//   * Checks if external storage is available for read and write
//   */
//  public boolean isExternalStorageWritable() {
//    return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
//  }
//
//  /**
//   * Checks if external storage is available to at least read
//   */
//  public boolean isExternalStorageReadable() {
//    final String state = Environment.getExternalStorageState();
//    return Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
//  }

  private boolean saveToGoogleDrive(final SharedPreferences prefs, final Context context, final byte[] encryptedBackup, final String backupFileName) {
    try {
      // 1 - retrieve existing backup so we know whether we have to create a new one, or update existing file
      final DriveResourceClient driveResourceClient = Drive.getDriveResourceClient(context, Objects.requireNonNull(BackupUtils.GoogleDrive.getSigninAccount(context)));
      final Task<DriveFolder> appFolderTask = driveResourceClient.getAppFolder();
      final Task<MetadataBuffer> metadataBufferTask = appFolderTask.continueWithTask(t ->
        ChannelsBackupBaseActivity.retrieveEclairBackupTask(appFolderTask, driveResourceClient, backupFileName));
      final MetadataBuffer buffer = Tasks.await(metadataBufferTask);

      // 2 - create or update backup file
      if (buffer.getCount() == 0) {
        Tasks.await(createBackupOnDrive(encryptedBackup, driveResourceClient, backupFileName));
      } else {
        Tasks.await(updateBackupOnDrive(encryptedBackup, driveResourceClient, buffer.get(0).getDriveId().asDriveFile()));
      }
      return true;
    } catch (Throwable t) {
      log.error("failed to save channels backup in google drive", t);
      return false;
    }
  }

  private Task<DriveFile> createBackupOnDrive(final byte[] encryptedBackup, final DriveResourceClient driveResourceClient, final String backupFileName) {
    final Task<DriveFolder> appFolderTask = driveResourceClient.getAppFolder();
    final Task<DriveContents> contentsTask = driveResourceClient.createContents();
    return Tasks.whenAll(appFolderTask, contentsTask).continueWithTask(task -> {

      // write encrypted backup as file content
      final DriveContents contents = contentsTask.getResult();
      final InputStream i = new ByteArrayInputStream(encryptedBackup);
      ByteStreams.copy(i, contents.getOutputStream());
      i.close();

      final MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
        .setTitle(backupFileName)
        .setCustomProperty(new CustomPropertyKey(Constants.BACKUP_META_DEVICE_ID, CustomPropertyKey.PUBLIC),
          WalletUtils.getDeviceId(getApplicationContext()))
        .setMimeType("application/octet-stream")
        .build();

      return driveResourceClient.createFile(appFolderTask.getResult(), changeSet, contents);
    });
  }

  private Task<Metadata> updateBackupOnDrive(final byte[] encryptedBackup, final DriveResourceClient driveResourceClient, final DriveFile driveFile) {
    return driveResourceClient.openFile(driveFile, DriveFile.MODE_WRITE_ONLY).continueWithTask(contentsTask -> {

      // write encrypted backup as file content
      final DriveContents contents = contentsTask.getResult();
      final InputStream i = new ByteArrayInputStream(encryptedBackup);
      ByteStreams.copy(i, contents.getOutputStream());
      i.close();

      return driveResourceClient.commitContents(contents, null);
    }).continueWithTask(aVoid -> {
      final MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
        .setCustomProperty(new CustomPropertyKey(Constants.BACKUP_META_DEVICE_ID, CustomPropertyKey.PUBLIC),
          WalletUtils.getDeviceId(getApplicationContext()))
        .build();

      return driveResourceClient.updateMetadata(driveFile, changeSet);
    });
  }
}
