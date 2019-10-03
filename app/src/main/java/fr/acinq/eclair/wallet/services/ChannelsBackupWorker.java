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

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.tasks.Tasks;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAuthIOException;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;
import com.google.common.io.Files;
import com.tozny.crypto.android.AesCbcWithIntegrity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import fr.acinq.bitcoin.ByteVector32;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.utils.BackupHelper;
import fr.acinq.eclair.wallet.utils.EncryptedBackup;
import fr.acinq.eclair.wallet.utils.EncryptedData;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import scodec.bits.ByteVector;

/**
 * Saves the eclair.sqlite backup file to an external storage folder (root/Eclair Mobile) and/or to Google Drive,
 * depending on the user's preferences. Local backup is mandatory.
 */
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

    if (!WalletUtils.getEclairDBFile(getApplicationContext()).exists()) {
      log.info("no eclair db file yet, aborting...");
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
      final boolean shouldBackupToDrive = BackupHelper.GoogleDrive.isGDriveAvailable(getApplicationContext()) && BackupHelper.GoogleDrive.getSigninAccount(getApplicationContext()) != null;
      boolean driveBackupSuccessful = true;
      if (shouldBackupToDrive) {
        driveBackupSuccessful = saveToGoogleDrive(getApplicationContext(), encryptedBackup, backupFileName);
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

  /**
   * This method creates an encrypted byte array from the eclair DB backup file (.bak) created by the eclair-core backup mechanism.
   * <p>
   * This backup is encrypted with the wallet's pk.
   *
   * @throws IOException
   * @throws GeneralSecurityException
   */
  private byte[] getEncryptedBackup(final Context context, final AesCbcWithIntegrity.SecretKeys sk) throws IOException, GeneralSecurityException {
    final File eclairDBFile = WalletUtils.getEclairDBFileBak(context);
    final EncryptedBackup backup = EncryptedBackup.encrypt(Files.toByteArray(eclairDBFile), sk, EncryptedBackup.BACKUP_VERSION_3);
    return backup.write();
  }

  private boolean saveToLocal(final byte[] encryptedBackup, final String backupFileName) {
    try {
      final File backupFile = BackupHelper.Local.getBackupFile(backupFileName);
      Files.write(encryptedBackup, backupFile);
      return true;
    } catch (Throwable t) {
      log.error("failed to save channels backup on local disk", t);
      return false;
    }
  }

  private boolean saveToGoogleDrive(final Context context, final byte[] encryptedBackup, final String backupFileName) {
    try {
      final String deviceId = WalletUtils.getDeviceId(context);
      final GoogleSignInAccount account = BackupHelper.GoogleDrive.getSigninAccount(context);
      if (account == null) {
        throw new GoogleAuthException();
      }

      // 1 - retrieve existing backup so we know whether we have to create a new one, or update existing file
      final Drive drive = Objects.requireNonNull(BackupHelper.GoogleDrive.getDriveServiceFromAccount(context, account), "drive service must not be null");
      final FileList backups = Tasks.await(BackupHelper.GoogleDrive.listBackups(Executors.newSingleThreadExecutor(), drive, backupFileName));
      final com.google.api.services.drive.model.File backup = BackupHelper.GoogleDrive.filterBestBackup(backups);

      // 2 - create or update backup file
      if (backup == null) {
        Tasks.await(BackupHelper.GoogleDrive.createBackup(Executors.newSingleThreadExecutor(), drive, backupFileName, encryptedBackup, deviceId));
        log.info("backup file successfully created on google drive");
      } else {
        Tasks.await(BackupHelper.GoogleDrive.updateBackup(Executors.newSingleThreadExecutor(), drive, backup.getId(), encryptedBackup, deviceId));
        log.info("backup file successfully updated on google drive");
      }
      return true;
    } catch (Throwable t) {
      log.error("failed to save channels backup on google drive", t);
      if (t instanceof GoogleAuthIOException || t instanceof GoogleAuthException) {
        BackupHelper.GoogleDrive.disableGDriveBackup(context);
      } else if (t.getCause() != null) {
        final Throwable cause = t.getCause();
        if (cause instanceof GoogleAuthIOException || cause instanceof GoogleAuthException) {
          BackupHelper.GoogleDrive.disableGDriveBackup(context);
        }
      }
      return false;
    }
  }

  public static void scheduleWorkASAP(final String seedHash, final ByteVector32 backupKey) {
    WorkManager.getInstance()
      .beginUniqueWork("ChannelsBackup", ExistingWorkPolicy.REPLACE, getOneTimeBackupRequest(seedHash, backupKey))
      .enqueue();
  }

  private static OneTimeWorkRequest getOneTimeBackupRequest(final String seedHash, final ByteVector32 backupKey) {
    return new OneTimeWorkRequest.Builder(ChannelsBackupWorker.class)
      .setInputData(new Data.Builder()
        .putString(ChannelsBackupWorker.BACKUP_NAME_INPUT, WalletUtils.getEclairBackupFileName(seedHash))
        .putString(ChannelsBackupWorker.BACKUP_KEY_INPUT, backupKey.toString())
        .build())
      .setInitialDelay(2, TimeUnit.SECONDS)
      .addTag("ChannelsBackupWork")
      .build();
  }
}
