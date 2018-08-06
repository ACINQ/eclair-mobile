package fr.acinq.eclair.wallet.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.internal.TaskUtil;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveClient;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;
import com.google.android.gms.drive.query.SortOrder;
import com.google.android.gms.drive.query.SortableField;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.Set;

import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.DeterministicWallet;
import fr.acinq.eclair.crypto.LocalKeyManager;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public abstract class GoogleDriveBaseActivity extends EclairActivity {

  static final String TAG = GoogleDriveBaseActivity.class.getSimpleName();

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

  protected DriveResourceClient getDriveResourceClient() {
    return mDriveResourceClient;
  }

  static class NoFilesFound extends RuntimeException {
    NoFilesFound(final String message) {
      super(message);
    }
  }
}
