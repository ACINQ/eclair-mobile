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

package fr.acinq.eclair.wallet.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.drive.*;
import com.google.android.gms.drive.query.*;
import com.google.android.gms.tasks.Task;
import fr.acinq.eclair.wallet.models.BackupTypes;
import fr.acinq.eclair.wallet.services.BackupUtils;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;

import java.util.HashMap;
import java.util.Map;

public abstract class ChannelsBackupBaseActivity extends EclairActivity {

  final static int ACCESS_REQUEST_PING_INTERVAL = 500;
  static final int GDRIVE_REQUEST_CODE_SIGN_IN = 0;
  private final Logger log = LoggerFactory.getLogger(ChannelsBackupBaseActivity.class);
  protected Map<BackupTypes, Option<Boolean>> accessRequestsMap = new HashMap<>();
  /**
   * Handles high-level drive functions like sync
   */
  private DriveClient mDriveClient;

  /**
   * Handle access to Drive resources/files.
   */
  private DriveResourceClient mDriveResourceClient;

  /**
   * Retrieve a backup file from Drive and returns a Task with its metadata.
   */
  public static Task<MetadataBuffer> retrieveEclairBackupTask(final Task<DriveFolder> appFolderTask,
                                                              final DriveResourceClient driveResourceClient,
                                                              final String backupFileName) {
    return appFolderTask.continueWithTask(appFolder -> {
      // retrieve file(s) from drive
      final SortOrder sortOrder = new SortOrder.Builder().addSortDescending(SortableField.MODIFIED_DATE).build();
      final Query query = new Query.Builder().addFilter(Filters.eq(SearchableField.TITLE, backupFileName))
        .setSortOrder(sortOrder).build();
      return driveResourceClient.queryChildren(appFolder.getResult(), query);
    });
  }

  protected void requestAccess(final boolean checkLocal, final boolean checkGdrive) {
    accessRequestsMap.clear();
    if (checkLocal || checkGdrive) {
      if (checkLocal) {
        accessRequestsMap.put(BackupTypes.LOCAL, Option.apply(null));
        requestLocalAccessOrApply();
      }
      if (checkGdrive) {
        accessRequestsMap.put(BackupTypes.GDRIVE, Option.apply(null));
        requestGDriveAccess();
      }
      new Handler().postDelayed(this::checkAccessRequestIsDone, ACCESS_REQUEST_PING_INTERVAL);
    } else {
      applyAccessRequestDone();
    }
  }

  private void checkAccessRequestIsDone() {
    if (accessRequestsMap.isEmpty()) {
      applyAccessRequestDone();
    } else {
      if (!accessRequestsMap.containsValue(Option.apply(null))) {
        applyAccessRequestDone();
      } else {
        new Handler().postDelayed(this::checkAccessRequestIsDone, ACCESS_REQUEST_PING_INTERVAL);
      }
    }
  }

  protected void applyAccessRequestDone() {
  }

  protected void requestLocalAccessOrApply() {
    if (!BackupUtils.Local.hasLocalAccess(this)) {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, Constants.PERMISSION_EXTERNAL_STORAGE_REQUEST);
    } else {
      applyLocalAccessGranted();
    }
  }

  protected void requestGDriveAccess() {
    final GoogleSignInAccount signInAccount = BackupUtils.GoogleDrive.getSigninAccount(getApplicationContext());
    final GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, getGoogleSigninOptions());
    if (signInAccount == null) {
      startActivityForResult(googleSignInClient.getSignInIntent(), GDRIVE_REQUEST_CODE_SIGN_IN);
    } else {
      googleSignInClient.revokeAccess()
        .addOnSuccessListener(aVoid -> startActivityForResult(googleSignInClient.getSignInIntent(), GDRIVE_REQUEST_CODE_SIGN_IN))
        .addOnFailureListener(e -> {
          log.error("could not revoke access to drive: ", e);
          applyGdriveAccessDenied();
        });
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (requestCode == Constants.PERMISSION_EXTERNAL_STORAGE_REQUEST) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        applyLocalAccessGranted();
      } else {
        applyLocalAccessDenied();
      }
    }
  }

  protected GoogleSignInOptions getGoogleSigninOptions() {
    return (new GoogleSignInOptions.Builder()).requestId().requestEmail().requestScopes(Drive.SCOPE_FILE).requestScopes(Drive.SCOPE_APPFOLDER).build();
  }

  Task<MetadataBuffer> retrieveEclairBackupTask() {
    final Task<DriveFolder> appFolderTask = getDriveResourceClient().getAppFolder();
    return retrieveEclairBackupTask(appFolderTask, getDriveResourceClient(), WalletUtils.getEclairBackupFileName(app.seedHash.get()));
  }

  @UiThread
  @CallSuper
  protected void applyLocalAccessDenied() {
    accessRequestsMap.put(BackupTypes.LOCAL, Option.apply(false));
  }

  @UiThread
  @CallSuper
  protected void applyLocalAccessGranted() {
    accessRequestsMap.put(BackupTypes.LOCAL, Option.apply(true));
  }

  @UiThread
  @CallSuper
  protected void applyGdriveAccessDenied() {
    accessRequestsMap.put(BackupTypes.GDRIVE, Option.apply(false));
  }

  @UiThread
  @CallSuper
  protected void applyGdriveAccessGranted(final GoogleSignInAccount signInAccount) {
    mDriveClient = Drive.getDriveClient(getApplicationContext(), signInAccount);
    mDriveResourceClient = Drive.getDriveResourceClient(getApplicationContext(), signInAccount);
    accessRequestsMap.put(BackupTypes.GDRIVE, Option.apply(true));
  }

  @Override
  protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == GDRIVE_REQUEST_CODE_SIGN_IN) {
      if (resultCode != RESULT_OK) {
        log.info("Google Drive sign-in failed with code {}");
        applyGdriveAccessDenied();
        return;
      }
      final Task<GoogleSignInAccount> getAccountTask = GoogleSignIn.getSignedInAccountFromIntent(data);
      final GoogleSignInAccount signInAccount = getAccountTask.getResult();
      if (getAccountTask.isSuccessful() && signInAccount != null) {
        applyGdriveAccessGranted(signInAccount);
      } else {
        log.info("Google Drive sign-in failed, could not get account");
        applyGdriveAccessDenied();
      }
    }
  }

  protected DriveResourceClient getDriveResourceClient() {
    return mDriveResourceClient;
  }

  protected DriveClient getDriveClient() {
    return mDriveClient;
  }
}
