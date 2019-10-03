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

package fr.acinq.eclair.wallet.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;

public interface BackupHelper {

  interface Local {

    static boolean isExternalStorageWritable() {
      return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    static boolean hasLocalAccess(final Context context) {
      return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    static File getBackupFile(final String backupFileName) throws EclairException.ExternalStorageUnavailableException {
      if (!isExternalStorageWritable()) {
        throw new EclairException.ExternalStorageUnavailableException();
      }

      final File storage = Environment.getExternalStorageDirectory();
      if (!storage.canWrite()) {
        throw new EclairException.ExternalStorageUnavailableException();
      }

      final File publicDir = new File(storage, Constants.ECLAIR_BACKUP_DIR);
      final File chainDir = new File(publicDir, BuildConfig.CHAIN);
      final File backup = new File(chainDir, backupFileName);

      if (!backup.exists()) {
        if (!chainDir.exists() && !chainDir.mkdirs()) {
          throw new EclairException.ExternalStorageUnavailableException();
        }
      }

      return backup;
    }
  }

  interface GoogleDrive {

    Logger log = LoggerFactory.getLogger(GoogleDrive.class);

    static Drive getDriveServiceFromAccount(final Context context, final GoogleSignInAccount signInAccount) {
      final GoogleAccountCredential credential = GoogleAccountCredential
        .usingOAuth2(context, BackupHelper.GoogleDrive.getGdriveScope())
        .setSelectedAccount(signInAccount.getAccount());
      return new Drive.Builder(new NetHttpTransport(), new GsonFactory(), credential)
        .setApplicationName(context.getString(R.string.app_name))
        .build();
    }

    static com.google.api.services.drive.model.File filterBestBackup(@NonNull FileList files) {
      log.debug("found {} backups on gdrive", files.getFiles().size());
      return files.getFiles().isEmpty() ? null : files.getFiles().get(0);
    }

    /**
     * Retrieves a list of (metadata) backup files from gdrive (including the hidden app data folder)
     */
    static Task<FileList> listBackupsLegacy(@NonNull final Executor executor, @NonNull final Drive drive, @NonNull final String fileName) {
      log.debug("retrieving list of backups from gdrive for name={} (including hidden app data folder)", fileName);
      return Tasks.call(executor, () -> drive.files().list()
        .setQ("name='" + fileName + "'")
        .setSpaces("drive,appDataFolder")
        .setFields("files(id,name,modifiedTime,appProperties)")
        .setOrderBy("modifiedTime desc")
        .execute());
    }

    /**
     * Retrieves a list of (metadata) backup files from gdrive. Only searches in the public eclair-mobile folder.
     */
    static Task<FileList> listBackups(@NonNull final Executor executor, @NonNull final Drive drive, @NonNull final String fileName) {
      log.debug("retrieving list of backups from gdrive for name={}", fileName);
      return Tasks.call(executor, () -> drive.files().list()
        .setQ("name='" + fileName + "'")
        .setSpaces("drive")
        .setFields("files(id,name,modifiedTime,appProperties)")
        .setOrderBy("modifiedTime desc")
        .execute());
    }

    static Task<byte[]> getFileContent(@NonNull final Executor executor, @NonNull final Drive drive, @NonNull final String fileId) {
      log.debug("retrieving file content from gdrive for id={}", fileId);
      return Tasks.call(executor, () -> {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        drive.files().get(fileId).executeMediaAndDownloadTo(baos);
        return baos.toByteArray();
      });
    }

    String BACKUP_FOLDER_NAME = "eclair-mobile";

    static com.google.api.services.drive.model.File createBackupFolderIfNeeded(@NonNull final Drive drive) throws IOException {

      // retrieve folder if it exists
      final FileList folders = drive.files().list()
        .setQ("name='" + BACKUP_FOLDER_NAME + "' and mimeType='application/vnd.google-apps.folder'")
        .setSpaces("drive")
        .setFields("files(id)").execute();

      if (folders.isEmpty() || folders.getFiles().isEmpty()) {
        final com.google.api.services.drive.model.File folderMeta = new com.google.api.services.drive.model.File();
        folderMeta.setParents(Collections.singletonList("root"))
          .setMimeType("application/vnd.google-apps.folder")
          .setName("eclair-mobile");
        return drive.files().create(folderMeta).setFields("id,parents,mimeType").execute();
      } else {
        return folders.getFiles().get(0);
      }
    }

    @NonNull
    static Task<String> createBackup(@NonNull final Executor executor, @NonNull final Drive drive, final String fileName, final byte[] encryptedData, final String deviceId) {
      log.info("creating new backup file on gdrive with name={}", fileName);
      return Tasks.call(executor, () -> {

        // 1 - create folder
        final com.google.api.services.drive.model.File folder = createBackupFolderIfNeeded(drive);

        // 2 - metadata
        final HashMap<String, String> props = new HashMap<>();
        props.put(Constants.BACKUP_META_DEVICE_ID, deviceId);
        final com.google.api.services.drive.model.File metadata = new com.google.api.services.drive.model.File()
          .setParents(Collections.singletonList(folder.getId()))
          .setAppProperties(props)
          .setMimeType("application/octet-stream")
          .setName(fileName);

        // 3 - content
        final ByteArrayContent content = new ByteArrayContent("application/octet-stream", encryptedData);

        // 4 - execute
        final com.google.api.services.drive.model.File file = drive.files()
          .create(metadata, content)
          .setFields("id,parents,appProperties")
          .execute();
        if (file == null) {
          throw new IOException("failed to create file on gdrive with null result");
        }
        return file.getId();
      });
    }

    @NonNull
    static Task<String> updateBackup(@NonNull final Executor executor, @NonNull final Drive drive, final String fileId, final byte[] encryptedData, final String deviceId) {
      log.info("updating backup file in gdrive with id={}", fileId);
      return Tasks.call(executor, () -> {

        // 1 - metadata
        final HashMap<String, String> props = new HashMap<>();
        props.put(Constants.BACKUP_META_DEVICE_ID, deviceId);
        final com.google.api.services.drive.model.File metadata = new com.google.api.services.drive.model.File()
          .setAppProperties(props)
          .setMimeType("application/octet-stream");

        // 2 - content
        final ByteArrayContent content = new ByteArrayContent("application/octet-stream", encryptedData);

        // 3 - execute
        final com.google.api.services.drive.model.File file = drive.files().update(fileId, metadata, content).execute();
        return file.getId();
      });
    }

    static boolean isGDriveAvailable(final Context context) {
      final int connectionResult = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
      if (connectionResult != ConnectionResult.SUCCESS) {
        log.info("Google play services are not available (code {})", connectionResult);
        return false;
      } else {
        return true;
      }
    }

    static Set<String> getGdriveScope() {
      final Set<String> requiredScopes = new HashSet<>(2);
      requiredScopes.add(DriveScopes.DRIVE_FILE);
      requiredScopes.add(DriveScopes.DRIVE_APPDATA);
      return requiredScopes;
    }

    static GoogleSignInAccount getSigninAccount(final Context context) {
      final GoogleSignInOptions opts = getGoogleSigninOptions();
      final GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
      if (GoogleSignIn.hasPermissions(account, opts.getScopeArray())) {
        return account;
      } else {
        log.info("gdrive sign-in account={} does not have correct permissions, revoking access", account);
        try {
          Tasks.await(Tasks.call(Executors.newSingleThreadExecutor(), () -> GoogleSignIn.getClient(context, opts).revokeAccess()));
        } catch (Exception e) {
          log.warn("could not revoke gdrive access: {}", e.getLocalizedMessage());
        }
        return null;
      }
    }

    static GoogleSignInOptions getGoogleSigninOptions() {
      return new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
        .requestScopes(new Scope(DriveScopes.DRIVE_APPDATA))
        .build();
    }

    static void disableGDriveBackup(final Context context) {
      PreferenceManager.getDefaultSharedPreferences(context).edit()
        .putBoolean(Constants.SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_ENABLED, false)
        .apply();
    }

    static void enableGDriveBackup(final Context context) {
      PreferenceManager.getDefaultSharedPreferences(context).edit()
        .putBoolean(Constants.SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_ENABLED, true)
        .apply();
    }

    static boolean isGDriveEnabled(final Context context) {
      return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Constants.SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_ENABLED, false);
    }
  }
}
