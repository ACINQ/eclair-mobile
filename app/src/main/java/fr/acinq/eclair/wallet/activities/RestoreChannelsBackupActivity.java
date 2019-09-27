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

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Html;
import android.widget.Toast;

import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.databinding.DataBindingUtil;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAuthIOException;
import com.google.api.client.util.DateTime;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import fr.acinq.bitcoin.ByteVector32;
import fr.acinq.eclair.channel.HasCommitments;
import fr.acinq.eclair.db.ChannelsDb;
import fr.acinq.eclair.db.sqlite.SqliteChannelsDb;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityRestoreChannelsBackupBinding;
import fr.acinq.eclair.wallet.models.BackupTypes;
import fr.acinq.eclair.wallet.utils.BackupHelper;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.EclairException;
import fr.acinq.eclair.wallet.utils.EncryptedBackup;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import scala.Option;
import scala.collection.Iterator;
import scala.collection.Seq;

public class RestoreChannelsBackupActivity extends ChannelsBackupBaseActivity {

  private static final Logger log = LoggerFactory.getLogger(RestoreChannelsBackupActivity.class);
  private ActivityRestoreChannelsBackupBinding mBinding;

  final static int SCAN_PING_INTERVAL = 2000;

  private Map<BackupTypes, Option<BackupScanResult>> mExpectedBackupsMap = new HashMap<>();
  private BackupScanOk mBestBackup = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_restore_channels_backup);
    mBinding.notFoundSkipButton.setOnClickListener(v -> ignoreRestoreAndBeDone());

    mBinding.scanButton.setOnClickListener(v -> {
      if (mBinding.requestLocalAccessCheckbox.isChecked() || mBinding.requestGdriveAccessCheckbox.isChecked()) {
        mBinding.setRestoreStep(Constants.RESTORE_BACKUP_REQUESTING_ACCESS);
        requestAccess(mBinding.requestLocalAccessCheckbox.isChecked(), mBinding.requestGdriveAccessCheckbox.isChecked());
      }
    });

    mBinding.tryAgainButton.setOnClickListener(v -> mBinding.setRestoreStep(Constants.RESTORE_BACKUP_INIT));
    mBinding.confirmRestoreButton.setOnClickListener(v -> restoreBestBackup());
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (app == null || app.seedHash == null || app.seedHash.get() == null) {
      finish();
    } else {
      mBinding.requestLocalAccessCheckbox.setChecked(BackupHelper.Local.isExternalStorageWritable());
      mBinding.setExternalStorageAvailable(BackupHelper.Local.isExternalStorageWritable());

      mBinding.requestGdriveAccessCheckbox.setChecked(BackupHelper.GoogleDrive.isGDriveAvailable(getApplicationContext()));
      mBinding.setGdriveAvailable(BackupHelper.GoogleDrive.isGDriveAvailable(getApplicationContext()));

      mBinding.seedHash.setText(getString(R.string.restorechannels_hash, app.seedHash.get()));
    }
  }

  @Override
  public void onBackPressed() {
    // user must no be able to go back if the backup has been restored
    if (!mBinding.getIsRestoring() || mBinding.getRestoreStep() != Constants.RESTORE_BACKUP_RESTORE_DONE) {
      super.onBackPressed();
    }
  }

  private void startScanning() {
    mBestBackup = null;
    mExpectedBackupsMap.clear();
    if (!accessRequestsMap.isEmpty()) {
      final boolean hasLocalAccess = accessRequestsMap.get(BackupTypes.LOCAL) != null && accessRequestsMap.get(BackupTypes.LOCAL).isDefined() && accessRequestsMap.get(BackupTypes.LOCAL).get();
      final boolean hasGdriveAccess = accessRequestsMap.get(BackupTypes.GDRIVE) != null && accessRequestsMap.get(BackupTypes.GDRIVE).isDefined() && accessRequestsMap.get(BackupTypes.GDRIVE).get();
      if (hasLocalAccess) {
        new Thread() {
          @Override
          public void run() {
            mBinding.setRestoreStep(Constants.RESTORE_BACKUP_SCANNING);
            mExpectedBackupsMap.put(BackupTypes.LOCAL, null);
            scanLocalDevice();
            if (hasGdriveAccess) {
              mExpectedBackupsMap.put(BackupTypes.GDRIVE, null);
              scanGoogleDrive();
            } else {
              log.info("no access to Google Drive, gdrive backups will not be scanned");
            }
            runOnUiThread(() -> new Handler().postDelayed(() -> checkScanningDone(), SCAN_PING_INTERVAL));
          }
        }.start();
      } else {
        mBinding.setRestoreStep(Constants.RESTORE_BACKUP_INIT);
        Toast.makeText(this, R.string.restorechannels_error_no_local_access_toast, Toast.LENGTH_LONG).show();
      }
    } else {
      mBinding.setRestoreStep(Constants.RESTORE_BACKUP_INIT);
    }
  }

  @UiThread
  private void checkScanningDone() {
    mBestBackup = null;
    if (mExpectedBackupsMap.isEmpty()) {
      mBinding.setRestoreStep(Constants.RESTORE_BACKUP_NOT_FOUND);
    } else {
      if (!mExpectedBackupsMap.containsValue(null)) { // scanning is done
        try {
          final BackupScanOk found = findBestBackup(mExpectedBackupsMap);
          if (found != null) {
            String origin = "";
            switch (found.type) {
              case LOCAL:
                origin = getString(R.string.restore_channels_origin_local);
                break;
              case GDRIVE:
                final GoogleSignInAccount gdriveAccount = BackupHelper.GoogleDrive.getSigninAccount(getApplicationContext());
                origin = getString(R.string.restore_channels_origin_gdrive,
                  gdriveAccount != null && gdriveAccount.getAccount() != null ? gdriveAccount.getAccount().name : getString(R.string.unknown));
                break;
            }
            mBinding.foundBackupTextDescOrigin.setText(Html.fromHtml(getString(R.string.restorechannels_found_desc_origin, origin)));
            mBinding.foundBackupTextDescChannelsCount.setText(Html.fromHtml(getString(R.string.restorechannels_found_desc_channels_count, found.localCommitIndexMap.size())));
            mBinding.foundBackupTextDescModified.setText(Html.fromHtml(getString(R.string.restorechannels_found_desc_modified, DateFormat.getDateTimeInstance().format(found.lastModified))));
            mBinding.setRestoreStep(found.isFromDevice ? Constants.RESTORE_BACKUP_FOUND : Constants.RESTORE_BACKUP_FOUND_WITH_CONFLICT);
          } else {
            mBinding.setRestoreStep(Constants.RESTORE_BACKUP_NOT_FOUND);
          }
          mBestBackup = found;
        } catch (EclairException.UnreadableBackupException e) {
          log.error("a backup file could not be read: ", e);
          mBinding.setRestoreStep(Constants.RESTORE_BACKUP_FOUND_ERROR);
          mBinding.errorText.setText(getString(R.string.restorechannels_error, e.type, e.getLocalizedMessage()));
        } catch (Throwable t) {
          log.error("error when handling scanning result: ", t);
          Toast.makeText(this, R.string.restorechannels_error_generic, Toast.LENGTH_LONG).show();
          mBinding.setRestoreStep(Constants.RESTORE_BACKUP_INIT);
        }
      } else {
        new Handler().postDelayed(this::checkScanningDone, SCAN_PING_INTERVAL);
      }
    }
  }

  private void restoreBestBackup() {
    if (mBestBackup != null && mBestBackup.file != null && mBestBackup.file.exists()) {
      mBinding.setIsRestoring(true);
      new Handler().postDelayed(() -> {
        new Thread() {
          @Override
          public void run() {
            try {
              WalletUtils.getChainDatadir(getApplicationContext()).mkdirs();
              Files.move(mBestBackup.file, WalletUtils.getEclairDBFile(getApplicationContext()));
              PreferenceManager.getDefaultSharedPreferences(getBaseContext())
                .edit()
                .putBoolean(Constants.SETTING_CHANNELS_RESTORE_DONE, true)
                .apply();
              mBinding.setRestoreStep(Constants.RESTORE_BACKUP_RESTORE_DONE);
              runOnUiThread(() -> new Handler().postDelayed(() -> finish(), 4000));
            } catch (IOException e) {
              log.error("error when moving " + mBestBackup.type + " backup file to eclair datadir: ", e);
              runOnUiThread(() -> Toast.makeText(getApplicationContext(), getString(R.string.restorechannels_restoring_error), Toast.LENGTH_LONG).show());
            } finally {
              mBinding.setIsRestoring(false);
            }
          }
        }.start();
      }, 750);
    } else {
      log.warn("best backup is empty or does not exist, and cannot be restored!");
    }
  }

  public static List<Map.Entry<BackupTypes, Option<BackupScanResult>>> sortBackupDateDesc(List<Map.Entry<BackupTypes, Option<BackupScanResult>>> set) {
    Collections.sort(set, (b1, b2) -> {
      if (b1.getValue().isDefined() && b1.getValue().get() instanceof BackupScanOk) {
        if (b2.getValue().isDefined() && b2.getValue().get() instanceof BackupScanOk) {
          return ((BackupScanOk) b2.getValue().get()).lastModified.compareTo(((BackupScanOk) b1.getValue().get()).lastModified);
        } else {
          return -1;
        }
      } else {
        return 1;
      }
    });
    return set;
  }

  @Override
  protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == GDRIVE_REQUEST_CODE_SIGN_IN) {
      handleGdriveSigninResult(resultCode, data);
    }
  }

  protected void handleGdriveSigninResult(final int resultCode, final Intent data) {
    if (resultCode != RESULT_OK) {
      log.info("Google Drive sign-in failed with code {}", resultCode);
      applyGdriveAccessDenied();
    } else {
      GoogleSignIn.getSignedInAccountFromIntent(data)
        .addOnSuccessListener(signInAccount -> {
          if (signInAccount == null) {
            log.info("Google Drive sign-in account is empty, deny access");
            applyGdriveAccessDenied();
          } else {
            applyGdriveAccessGranted(signInAccount);
          }
        })
        .addOnFailureListener(e -> {
          log.info("Google Drive sign-in failed, could not get account: ", e);
          applyGdriveAccessDenied();
        });
    }
  }

  @Nullable
  public static BackupScanOk findBestBackup(Map<BackupTypes, Option<BackupScanResult>> backupsSet) throws EclairException.UnreadableBackupException {
    BackupScanOk bestBackupYet = null;
    final List<Map.Entry<BackupTypes, Option<BackupScanResult>>> sortedBackups = sortBackupDateDesc(new ArrayList<>(backupsSet.entrySet()));
    for (final Map.Entry<BackupTypes, Option<BackupScanResult>> scan : sortedBackups) {
      final BackupTypes type = scan.getKey();
      final Option<BackupScanResult> result_opt = scan.getValue();
      if (result_opt != null && result_opt.isDefined()) {
        final BackupScanResult result = result_opt.get();
        if (result instanceof BackupScanOk) {
          final BackupScanOk challenger = (BackupScanOk) result;
          if (bestBackupYet == null) {
            bestBackupYet = challenger; // first element is best by default (it's the most recent backup)
          } else {
            final Sets.SetView<ByteVector32> channelsIntersection = Sets.intersection(bestBackupYet.localCommitIndexMap.keySet(), challenger.localCommitIndexMap.keySet());
            if (channelsIntersection.size() < bestBackupYet.localCommitIndexMap.size()) {
              log.info("(best) {} and (challenger) {} have only {} channels in common, challenger is ignored", bestBackupYet.type, challenger.type, channelsIntersection.size());
            } else {
              // Compare current backup with the current best option by using a score ; if score > 0, challenger becomes the new best.
              short score = 0;
              final MapDifference<ByteVector32, Long> diff = Maps.difference(bestBackupYet.localCommitIndexMap, challenger.localCommitIndexMap);
              for (MapDifference.ValueDifference<Long> d : diff.entriesDiffering().values()) {
                if (d.leftValue() >= d.rightValue()) score -= 1;
                else score += 1;
              }
              log.info("relative difference between (best) {} and (challenger) {} = {}", bestBackupYet.type, challenger.type, score);
              if (score > 0) {
                bestBackupYet = challenger;
              }
            }
          }
        } else if (result instanceof BackupScanFailure) {
          final BackupScanFailure failure = (BackupScanFailure) result;
          throw new EclairException.UnreadableBackupException(type, failure.message);
        } else {
          throw new RuntimeException("unhandled backup result: " + result);
        }
      }
    }
    return bestBackupYet;
  }

  private void scanLocalDevice() {
    try {
      final File backup = BackupHelper.Local.getBackupFile(WalletUtils.getEclairBackupFileName(app.seedHash.get()));
      if (!backup.exists()) {
        log.info("no LOCAL backup file found for this seed");
        mExpectedBackupsMap.put(BackupTypes.LOCAL, Option.apply(null));
      } else {
        final BackupScanOk localBackup = decryptFile(Files.toByteArray(backup), new Date(backup.lastModified()), BackupTypes.LOCAL);
        mExpectedBackupsMap.put(BackupTypes.LOCAL, Option.apply(localBackup));
      }
    } catch (EclairException.ExternalStorageUnavailableException e) {
      log.error("external storage not available: ", e);
      runOnUiThread(() -> Toast.makeText(this, R.string.restorechannels_error_external_storage_toast, Toast.LENGTH_LONG).show());
      mExpectedBackupsMap.put(BackupTypes.LOCAL, Option.apply(null));
    } catch (Throwable t) {
      log.error("could not read local backup file: ", t);
      mExpectedBackupsMap.put(BackupTypes.LOCAL, Option.apply(new BackupScanFailure(t.getLocalizedMessage())));
    }
  }

  @WorkerThread
  private void scanGoogleDrive() {
    log.debug("starting to scan gdrive for backups");
    final Executor executor = Executors.newSingleThreadExecutor();
    // use legacy list method to also retrieve old backup that could have been left in the hidden app data folder.
    BackupHelper.GoogleDrive.listBackupsLegacy(Executors.newSingleThreadExecutor(), mDrive, WalletUtils.getEclairBackupFileName(app.seedHash.get()))
      .addOnSuccessListener(files -> {
        log.info("found {} backup files on gdrive for this seed: {}", files.getFiles().size(), files);
        final com.google.api.services.drive.model.File backup = BackupHelper.GoogleDrive.filterBestBackup(files);
        if (backup == null) {
          mExpectedBackupsMap.put(BackupTypes.GDRIVE, Option.apply(null));
        } else {
          final DateTime modifiedDate = backup.getModifiedTime();
          log.info("best backup from gdrive is {}", backup);
          final String currentDeviceId = WalletUtils.getDeviceId(getApplicationContext());
          final Map<String, String> props = backup.getAppProperties();
          final String remoteDeviceId = props != null ? props.get(Constants.BACKUP_META_DEVICE_ID) : currentDeviceId;
          BackupHelper.GoogleDrive.getFileContent(executor, mDrive, backup.getId())
            .addOnSuccessListener(content -> {
              try {
                final BackupScanOk gdriveBackup = decryptFile(content, new Date(modifiedDate.getValue()), BackupTypes.GDRIVE);
                gdriveBackup.setIsFromDevice(remoteDeviceId == null || currentDeviceId.equals(remoteDeviceId));
                mExpectedBackupsMap.put(BackupTypes.GDRIVE, Option.apply(gdriveBackup));
                log.debug("successfully retrieved backup file from gdrive");
              } catch (Throwable t) {
                log.error("could not read backup file from gdrive: ", t);
                mExpectedBackupsMap.put(BackupTypes.GDRIVE, Option.apply(new BackupScanFailure(t.getLocalizedMessage())));
              } finally {
                log.debug("finished gdrive scan");
              }
            })
            .addOnFailureListener(e -> {
              log.error("could not retrieve backup content from gdrive: ", e);
              Toast.makeText(getApplicationContext(), R.string.restorechannels_gdrive_scan_error, Toast.LENGTH_LONG).show();
              mExpectedBackupsMap.put(BackupTypes.GDRIVE, Option.apply(null));
            });
        }
      })
      .addOnFailureListener(e -> {
        log.error("could not retrieve backup files from gdrive: ", e);
        if (e instanceof GoogleAuthIOException || e instanceof GoogleAuthException) {
          mExpectedBackupsMap.clear();
          mBinding.setRestoreStep(Constants.RESTORE_BACKUP_REQUESTING_ACCESS);
          requestAccess(mBinding.requestLocalAccessCheckbox.isChecked(), mBinding.requestGdriveAccessCheckbox.isChecked());
        } else {
          Toast.makeText(getApplicationContext(), R.string.restorechannels_gdrive_scan_error, Toast.LENGTH_LONG).show();
          mExpectedBackupsMap.put(BackupTypes.GDRIVE, Option.apply(null));
        }
      });
  }

  @WorkerThread
  private BackupScanOk decryptFile(final byte[] content, final Date modified, final BackupTypes type) throws IOException, GeneralSecurityException, SQLException, ClassNotFoundException {

    // 1 - retrieve, decrypt and write backup file to datadir
    final EncryptedBackup encryptedContent = EncryptedBackup.read(content);
    final byte[] decryptedContent = encryptedContent.decrypt(EncryptedBackup.secretKeyFromBinaryKey(EncryptedBackup.BACKUP_VERSION_1 == encryptedContent.getVersion() ? app.backupKey_v1.get() : app.backupKey_v2.get()));
    log.debug("successfully decrypted backup from {}", type);
    final File decryptedFile = new File(WalletUtils.getDatadir(getApplicationContext()), type.toString() + "-restore.sqlite.tmp");
    Files.write(decryptedContent, decryptedFile);

    // 2 - read backup file and extracts relevant data (channels count + commitments)
    Class.forName("org.sqlite.JDBC");
    final Connection decryptedFileConn = DriverManager.getConnection("jdbc:sqlite:" + decryptedFile.getPath());
    final ChannelsDb db = new SqliteChannelsDb(decryptedFileConn);
    final Seq<HasCommitments> commitments = db.listLocalChannels();
    final Map<ByteVector32, Long> localCommitIndexMap = new HashMap<>();
    final Iterator<HasCommitments> iterator = commitments.iterator();
    while (iterator.hasNext()) {
      final HasCommitments hc = iterator.next();
      localCommitIndexMap.put(hc.channelId(), hc.commitments().localCommit().index());
    }
    db.close();

    // 3 - returns a successfully read backup object
    log.info("found {} channels in backup file from {}", localCommitIndexMap.size(), type);
    return new BackupScanOk(type, localCommitIndexMap, modified, decryptedFile);
  }

  private void ignoreRestoreAndBeDone() {
    getCustomDialog(R.string.restorechannels_skip_backup_confirmation)
      .setPositiveButton(R.string.btn_confirm, (dialog, which) -> {
        PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit()
          .putBoolean(Constants.SETTING_CHANNELS_RESTORE_DONE, true).commit();
        finish();
      })
      .setNegativeButton(R.string.btn_cancel, null)
      .create()
      .show();
  }

  @Override
  protected void applyAccessRequestDone() {
    startScanning();
  }

  @Override
  protected void applyGdriveAccessDenied() {
    super.applyGdriveAccessDenied();
    BackupHelper.GoogleDrive.disableGDriveBackup(getApplicationContext());
  }

  @Override
  protected void applyGdriveAccessGranted(GoogleSignInAccount signIn) {
    super.applyGdriveAccessGranted(signIn);
    BackupHelper.GoogleDrive.enableGDriveBackup(getApplicationContext());
  }

  public interface BackupScanResult {
  }

  public static class BackupScanFailure implements BackupScanResult {
    final String message;

    public BackupScanFailure(final String message) {
      this.message = message;
    }
  }

  public static class BackupScanOk implements BackupScanResult {
    public final BackupTypes type;
    public final Map<ByteVector32, Long> localCommitIndexMap;
    public final Date lastModified;
    public final File file;
    private boolean isFromDevice = true;

    public BackupScanOk(final BackupTypes type, final Map<ByteVector32, Long> localCommitIndexMap, final Date lastModified, final File file) {
      this.type = type;
      this.localCommitIndexMap = localCommitIndexMap;
      this.lastModified = lastModified;
      this.file = file;
    }

    String printLocalCommitIndexMap() {
      return Arrays.toString(localCommitIndexMap.entrySet().toArray());
    }

    void setIsFromDevice(final boolean isFromDevice) {
      this.isFromDevice = isFromDevice;
    }
  }
}
