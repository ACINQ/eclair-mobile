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

import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Html;
import android.widget.Toast;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.databinding.DataBindingUtil;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.metadata.CustomPropertyKey;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import fr.acinq.bitcoin.ByteVector32;
import fr.acinq.eclair.channel.HasCommitments;
import fr.acinq.eclair.db.ChannelsDb;
import fr.acinq.eclair.db.sqlite.SqliteChannelsDb;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityRestoreChannelsBackupBinding;
import fr.acinq.eclair.wallet.models.BackupTypes;
import fr.acinq.eclair.wallet.services.BackupUtils;
import fr.acinq.eclair.wallet.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.collection.Iterator;
import scala.collection.Seq;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.*;

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
      mBinding.requestLocalAccessCheckbox.setChecked(BackupUtils.Local.isExternalStorageWritable());
      mBinding.setExternalStorageAvailable(BackupUtils.Local.isExternalStorageWritable());

      mBinding.requestGdriveAccessCheckbox.setChecked(BackupUtils.GoogleDrive.isGDriveAvailable(getApplicationContext()));
      mBinding.setGdriveAvailable(BackupUtils.GoogleDrive.isGDriveAvailable(getApplicationContext()));

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
                final GoogleSignInAccount gdriveAccount = BackupUtils.GoogleDrive.getSigninAccount(getApplicationContext());
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
      final File backup = BackupUtils.Local.getBackupFile(WalletUtils.getEclairBackupFileName(app.seedHash.get()));
      if (!backup.exists()) {
        log.info("no local backup file found for this seed");
        mExpectedBackupsMap.put(BackupTypes.LOCAL, Option.apply(null));
      } else {
        final BackupScanOk localBackup = decryptFile(Files.toByteArray(backup), new Date(backup.lastModified()), BackupTypes.LOCAL);
        log.debug("successfully retrieved local backup file");
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
    getDriveClient().requestSync()
      .continueWithTask(aVoid -> retrieveEclairBackupTask())
      .addOnSuccessListener(metadataBuffer -> {
        if (metadataBuffer.getCount() > 0) {
          final Metadata metadata = metadataBuffer.get(0);
          final Date modifiedDate = metadata.getModifiedDate();
          final String remoteDeviceId = metadata.getCustomProperties().get(new CustomPropertyKey(Constants.BACKUP_META_DEVICE_ID, CustomPropertyKey.PUBLIC));
          final String deviceId = WalletUtils.getDeviceId(getApplicationContext());
          getDriveResourceClient().openFile(metadata.getDriveId().asDriveFile(), DriveFile.MODE_READ_ONLY)
            .addOnSuccessListener(driveFileContents -> {
              try {
                // read file content
                final InputStream driveInputStream = driveFileContents.getInputStream();
                final byte[] content = new byte[driveInputStream.available()];
                driveInputStream.read(content);
                // decrypt content
                final BackupScanOk gdriveBackup = decryptFile(content, modifiedDate, BackupTypes.GDRIVE);
                gdriveBackup.setIsFromDevice(remoteDeviceId == null || deviceId.equals(remoteDeviceId));
                mExpectedBackupsMap.put(BackupTypes.GDRIVE, Option.apply(gdriveBackup));
                log.debug("successfully retrieved backup file from gdrive");
              } catch (Throwable t) {
                log.error("could not read backup file from gdrive: ", t);
                mExpectedBackupsMap.put(BackupTypes.GDRIVE, Option.apply(new BackupScanFailure(t.getLocalizedMessage())));
              } finally {
                log.debug("finished scan gdrive");
                getDriveResourceClient().discardContents(driveFileContents);
              }
            });
        } else {
          log.info("no backup file found on gdrive for this seed");
          mExpectedBackupsMap.put(BackupTypes.GDRIVE, Option.apply(null));
        }
      })
      .addOnFailureListener(e -> {
        log.error("could not retrieve data from gdrive: ", e);
        mExpectedBackupsMap.put(BackupTypes.GDRIVE, Option.apply(null));
      });
  }

  @WorkerThread
  private BackupScanOk decryptFile(final byte[] content, final Date modified, final BackupTypes type) throws IOException, GeneralSecurityException, SQLException, ClassNotFoundException {
    log.debug("decrypting backup file from {}", type);

    // 1 - retrieve, decrypt and write backup file to datadir
    final EncryptedBackup encryptedContent = EncryptedBackup.read(content);
    final byte[] decryptedContent = encryptedContent.decrypt(EncryptedData.secretKeyFromBinaryKey(EncryptedBackup.BACKUP_VERSION_1 == encryptedContent.getVersion() ? app.backupKey_v1.get() : app.backupKey_v2.get()));
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
    BackupUtils.GoogleDrive.disableGDriveBackup(getApplicationContext());
  }

  @Override
  protected void applyGdriveAccessGranted(GoogleSignInAccount signIn) {
    super.applyGdriveAccessGranted(signIn);
    BackupUtils.GoogleDrive.enableGDriveBackup(getApplicationContext());
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
