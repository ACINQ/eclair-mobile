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
import android.content.pm.PackageManager;
import android.os.Handler;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.api.services.drive.Drive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import fr.acinq.eclair.wallet.models.BackupTypes;
import fr.acinq.eclair.wallet.utils.BackupHelper;
import fr.acinq.eclair.wallet.utils.Constants;
import scala.Option;

public abstract class ChannelsBackupBaseActivity extends EclairActivity {

  final static int ACCESS_REQUEST_PING_INTERVAL = 1500;
  static final int GDRIVE_REQUEST_CODE_SIGN_IN = 0;
  private final Logger log = LoggerFactory.getLogger(ChannelsBackupBaseActivity.class);

  /**
   * This maps monitors the access status for each backup sources requested by the user. Access is often
   * granted asynchronously and may not be known.
   * - If the value is None, the source's access status is pending.
   * - If the value is Some(true), access is granted.
   * - If the value is Some(false), access is denied.
   * <p>
   * Value should never be null.
   */
  protected Map<BackupTypes, Option<Boolean>> accessRequestsMap = new HashMap<>();

  /**
   * Drive service to manage files using the REST v3 api.
   */
  protected Drive mDrive;

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
      checkAccessRequestIsDone();
    } else {
      applyAccessRequestDone();
    }
  }

  private Handler accessCheckHandler = new Handler();

  private void checkAccessRequestIsDone() {
    accessCheckHandler.removeCallbacksAndMessages(null);
    if (accessRequestsMap.isEmpty()) {
      applyAccessRequestDone();
    } else {
      if (!accessRequestsMap.containsValue(Option.apply(null))) {
        applyAccessRequestDone();
      } else {
        accessCheckHandler.postDelayed(this::checkAccessRequestIsDone, ACCESS_REQUEST_PING_INTERVAL);
      }
    }
  }

  protected void applyAccessRequestDone() {
  }

  protected void requestLocalAccessOrApply() {
    if (!BackupHelper.Local.hasLocalAccess(this)) {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, Constants.PERMISSION_EXTERNAL_STORAGE_REQUEST);
    } else {
      applyLocalAccessGranted();
    }
  }

  protected void requestGDriveAccess() {
    final GoogleSignInAccount signInAccount = BackupHelper.GoogleDrive.getSigninAccount(getApplicationContext());
    final GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, BackupHelper.GoogleDrive.getGoogleSigninOptions());
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
    mDrive = BackupHelper.GoogleDrive.getDriveServiceFromAccount(getApplicationContext(), signInAccount);
    accessRequestsMap.put(BackupTypes.GDRIVE, Option.apply(true));
  }
}
