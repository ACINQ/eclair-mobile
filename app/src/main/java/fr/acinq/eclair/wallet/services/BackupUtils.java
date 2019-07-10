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

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.preference.PreferenceManager;
import androidx.core.content.ContextCompat;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.drive.Drive;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.EclairException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public interface BackupUtils {

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

    static boolean isGDriveAvailable(final Context context) {
      final int connectionResult = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
      if (connectionResult != ConnectionResult.SUCCESS) {
        log.info("Google play services are not available (code {})", connectionResult);
        return false;
      } else {
        return true;
      }
    }

    static GoogleSignInAccount getSigninAccount(final Context context) {
      final Set<Scope> requiredScopes = new HashSet<>(2);
      requiredScopes.add(Drive.SCOPE_FILE);
      requiredScopes.add(Drive.SCOPE_APPFOLDER);
      final GoogleSignInAccount signInAccount = GoogleSignIn.getLastSignedInAccount(context);
      if (signInAccount != null && signInAccount.getGrantedScopes().containsAll(requiredScopes)) {
        return signInAccount;
      } else {
        return null;
      }
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
