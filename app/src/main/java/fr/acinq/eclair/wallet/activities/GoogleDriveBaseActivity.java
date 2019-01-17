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

import android.content.Context;
import android.content.Intent;
import android.support.annotation.UiThread;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveClient;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;
import com.google.android.gms.drive.query.SortOrder;
import com.google.android.gms.drive.query.SortableField;
import com.google.android.gms.tasks.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import fr.acinq.eclair.wallet.utils.WalletUtils;

public abstract class GoogleDriveBaseActivity extends EclairActivity {

  private final Logger log = LoggerFactory.getLogger(GoogleDriveBaseActivity.class);

  static final int REQUEST_CODE_SIGN_IN = 0;

  /**
   * Handles high-level drive functions like sync
   */
  private DriveClient mDriveClient;

  /**
   * Handle access to Drive resources/files.
   */
  private DriveResourceClient mDriveResourceClient;

  public static GoogleSignInAccount getSigninAccount(final Context context) {
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

  protected GoogleSignInOptions getGoogleSigninOptions() {
    return (new GoogleSignInOptions.Builder()).requestId().requestEmail().requestScopes(Drive.SCOPE_FILE).requestScopes(Drive.SCOPE_APPFOLDER).build();
  }

  /**
   * Start sign in activity if needed, or init drive client.
   */
  protected void initOrSignInGoogleDrive() {
    final GoogleSignInAccount signInAccount = getSigninAccount(getApplicationContext());
    if (signInAccount == null) {
      final GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, getGoogleSigninOptions());
      startActivityForResult(googleSignInClient.getSignInIntent(), REQUEST_CODE_SIGN_IN);
    } else {
      initializeDriveClient(signInAccount);
    }
  }

  void initializeDriveClient(final GoogleSignInAccount signInAccount) {
    mDriveClient = Drive.getDriveClient(getApplicationContext(), signInAccount);
    mDriveResourceClient = Drive.getDriveResourceClient(getApplicationContext(), signInAccount);
    onDriveClientReady(signInAccount);
  }

  abstract void onDriveClientReady(final GoogleSignInAccount signInAccount);

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

  static byte[] getBytesFromDriveContents(final DriveContents contents) throws IOException {
    InputStream driveInputStream = contents.getInputStream();
    byte[] buffer = new byte[driveInputStream.available()];
    driveInputStream.read(buffer);
    return buffer;
  }

  Task<MetadataBuffer> retrieveEclairBackupTask() {
    final Task<DriveFolder> appFolderTask = getDriveResourceClient().getAppFolder();
    return retrieveEclairBackupTask(appFolderTask, getDriveResourceClient(), WalletUtils.getEclairBackupFileName(app.seedHash.get()));
  }

  @UiThread
  abstract void applyAccessDenied();
  @UiThread
  abstract void applyAccessGranted(final GoogleSignInAccount signIn);

  @Override
  protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    switch (requestCode) {
      case REQUEST_CODE_SIGN_IN:
        if (resultCode != RESULT_OK) {
          log.info("Google Drive sign-in failed with code {}");
          applyAccessDenied();
          return;
        }
        final Task<GoogleSignInAccount> getAccountTask = GoogleSignIn.getSignedInAccountFromIntent(data);
        if (getAccountTask.isSuccessful()) {
          initializeDriveClient(getAccountTask.getResult());
        } else {
          log.info("Google Drive sign-in failed, could not get account");
          applyAccessDenied();
        }
        break;
    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  protected void checkAccess() {
    new Thread() {
      @Override
      public void run() {
        final GoogleSignInAccount signInAccount = getSigninAccount(getApplicationContext());
        if (signInAccount != null) {
          initializeDriveClient(signInAccount);
        } else {
          log.info("Google Drive signin account is null");
          runOnUiThread(() -> applyAccessDenied());
        }
      }
    }.start();
  }

  protected DriveResourceClient getDriveResourceClient() {
    return mDriveResourceClient;
  }
  protected DriveClient getDriveClient() {
    return mDriveClient;
  }
}
