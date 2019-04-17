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

import android.annotation.SuppressLint;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.WorkerThread;
import android.view.MotionEvent;
import android.widget.Toast;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.metadata.CustomPropertyKey;
import com.google.common.io.Files;
import fr.acinq.eclair.channel.HasCommitments;
import fr.acinq.eclair.db.ChannelsDb;
import fr.acinq.eclair.db.sqlite.SqliteChannelsDb;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityRestoreChannelsBackupBinding;
import fr.acinq.eclair.wallet.models.BackupTypes;
import fr.acinq.eclair.wallet.services.BackupUtils;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.EncryptedBackup;
import fr.acinq.eclair.wallet.utils.EncryptedData;
import fr.acinq.eclair.wallet.utils.WalletUtils;
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

  private final Logger log = LoggerFactory.getLogger(RestoreChannelsBackupActivity.class);
  private ActivityRestoreChannelsBackupBinding mBinding;

  final static int SCAN_PING_INTERVAL = 2000;

  private Map<BackupTypes, Option<BackupScan>> expectedBackupsMap = new HashMap<>();

  @SuppressLint("ClickableViewAccessibility")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_restore_channels_backup);
    mBinding.notFoundSkipButton.setOnClickListener(v -> ignoreRestoreAndBeDone());

    mBinding.requestLocalAccessCheckbox.setChecked(true);
    mBinding.requestGdriveAccessCheckbox.setChecked(true);

    mBinding.scanButton.setOnClickListener(v -> {
      if (mBinding.requestLocalAccessCheckbox.isChecked() || mBinding.requestGdriveAccessCheckbox.isChecked()) {
        mBinding.setRestoreStep(Constants.RESTORE_BACKUP_REQUESTING_ACCESS);
        new Handler().postDelayed(() -> requestAccess(mBinding.requestLocalAccessCheckbox.isChecked(), mBinding.requestGdriveAccessCheckbox.isChecked()), ACCESS_REQUEST_PING_INTERVAL);
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
    log.debug("start scanning");
    expectedBackupsMap.clear();
    if (!accessRequestsMap.isEmpty()) {
      new Thread() {
        @Override
        public void run() {
          mBinding.setRestoreStep(Constants.RESTORE_BACKUP_SCANNING);
          if (accessRequestsMap.get(BackupTypes.LOCAL) != null && accessRequestsMap.get(BackupTypes.LOCAL).isDefined() && accessRequestsMap.get(BackupTypes.LOCAL).get()) {
            expectedBackupsMap.put(BackupTypes.LOCAL, null);
            scanLocalDevice();
          }
          if (accessRequestsMap.get(BackupTypes.GDRIVE) != null && accessRequestsMap.get(BackupTypes.GDRIVE).isDefined() && accessRequestsMap.get(BackupTypes.GDRIVE).get()) {
            expectedBackupsMap.put(BackupTypes.GDRIVE, null);
            scanGoogleDrive();
          }
          runOnUiThread(() -> new Handler().postDelayed(() -> checkScanningDone(), SCAN_PING_INTERVAL));
        }
      }.start();
    } else {
      mBinding.setRestoreStep(Constants.RESTORE_BACKUP_INIT);
    }
  }

  private void checkScanningDone() {
    if (expectedBackupsMap.isEmpty()) {
      mBinding.setRestoreStep(Constants.RESTORE_BACKUP_NOT_FOUND);
    } else {
      if (!expectedBackupsMap.containsValue(null)) { // scanning is done
        final BackupScan found = getBestBackupScan();
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
          mBinding.foundBackupTextDesc.setText(getString(R.string.restorechannels_found_desc,
            origin, found.channelsCount, Collections.max(found.commitmentCounts), DateFormat.getDateTimeInstance().format(found.lastModified)));
          mBinding.setRestoreStep(found.isFromDevice ? Constants.RESTORE_BACKUP_FOUND : Constants.RESTORE_BACKUP_FOUND_WITH_CONFLICT);
        } else {
          mBinding.setRestoreStep(Constants.RESTORE_BACKUP_NOT_FOUND);
        }
      } else {
        new Handler().postDelayed(this::checkScanningDone, SCAN_PING_INTERVAL);
      }
    }
  }

  private void restoreBestBackup() {
    final BackupScan bestBackup = getBestBackupScan();
    if (bestBackup != null && bestBackup.file != null && bestBackup.file.exists()) {
      mBinding.setIsRestoring(true);
      new Handler().postDelayed(() -> {
        new Thread() {
          @Override
          public void run() {
            try {
              WalletUtils.getChainDatadir(getApplicationContext()).mkdirs();
              Files.move(bestBackup.file, WalletUtils.getEclairDBFile(getApplicationContext()));
              PreferenceManager.getDefaultSharedPreferences(getBaseContext())
                .edit()
                .putBoolean(Constants.SETTING_CHANNELS_RESTORE_DONE, true)
                .apply();
              mBinding.setRestoreStep(Constants.RESTORE_BACKUP_RESTORE_DONE);
              runOnUiThread(() -> new Handler().postDelayed(() -> finish(), 4000));
            } catch (IOException e) {
              log.error("error when moving " + bestBackup.type + " backup file to eclair datadir: ", e);
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

  @Nullable
  private BackupScan getBestBackupScan() {
    long maxCommitmentIndex = 0;
    BackupScan bestBackupYet = null;
    for (final Option<BackupScan> backup_opt : expectedBackupsMap.values()) {
      if (backup_opt != null && backup_opt.isDefined()) {
        final BackupScan backup = backup_opt.get();
        final long commitmentsIndexCount = Collections.max(backup.commitmentCounts);
        log.info("we have backup from {} with {} channels and {} max commitment", backup_opt.get().type, backup.channelsCount, commitmentsIndexCount);
        if (commitmentsIndexCount > maxCommitmentIndex) {
          maxCommitmentIndex = commitmentsIndexCount;
          bestBackupYet = backup;
        }
      }
    }
    return bestBackupYet;
  }

  private void scanLocalDevice() {
    log.debug("starting scan local device");
    try {
      final File backup = BackupUtils.Local.getBackupFile(WalletUtils.getEclairBackupFileName(app.seedHash.get()));
      if (!backup.exists()) {
        log.info("no local backup file found for this seed");
        expectedBackupsMap.put(BackupTypes.LOCAL, Option.apply(null));
      } else {
        final BackupScan localBackup = decryptFile(Files.toByteArray(backup), new Date(backup.lastModified()), BackupTypes.LOCAL);
        log.debug("successfully retrieved local backup file");
        expectedBackupsMap.put(BackupTypes.LOCAL, Option.apply(localBackup));
      }
    } catch (Throwable t) {
      log.error("could not read local backup file: ", t);
      expectedBackupsMap.put(BackupTypes.LOCAL, Option.apply(null));
    } finally {
      log.debug("finished scan local device");
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

                final BackupScan gdriveBackup = decryptFile(content, modifiedDate, BackupTypes.GDRIVE);
                gdriveBackup.setIsFromDevice(remoteDeviceId == null || deviceId.equals(remoteDeviceId));
                expectedBackupsMap.put(BackupTypes.GDRIVE, Option.apply(gdriveBackup));
                log.debug("successfully retrieved backup file from gdrive");
              } catch (Throwable t) {
                log.error("could not read backup file from gdrive: ", t);
                expectedBackupsMap.put(BackupTypes.GDRIVE, Option.apply(null));
              } finally {
                log.debug("finished scan gdrive");
                getDriveResourceClient().discardContents(driveFileContents);
              }
            });
        } else {
          log.info("no backup file found on gdrive for this seed");
          expectedBackupsMap.put(BackupTypes.GDRIVE, Option.apply(null));
        }
      })
      .addOnFailureListener(e -> {
        log.error("could not retrieve data from gdrive: ", e);
        expectedBackupsMap.put(BackupTypes.GDRIVE, Option.apply(null));
      });
  }

  @WorkerThread
  private BackupScan decryptFile(final byte[] content, final Date modified, final BackupTypes type) throws IOException, GeneralSecurityException, SQLException {
    log.debug("decrypting backup file from {}", type);
    final EncryptedBackup encryptedContent = EncryptedBackup.read(content);
    final byte[] decryptedContent = encryptedContent.decrypt(EncryptedData.secretKeyFromBinaryKey(
      // backward compatibility code for v0.3.6-TESTNET which uses backup version 1
      EncryptedBackup.BACKUP_VERSION_1 == encryptedContent.getVersion() ? app.backupKey_v1.get() : app.backupKey_v2.get()));

    final File datadir = WalletUtils.getDatadir(getApplicationContext());
    final File decryptedFile = new File(datadir, type.toString() + "-restore.sqlite.tmp");
    Files.write(decryptedContent, decryptedFile);
    final Connection decryptedFileConn = DriverManager.getConnection("jdbc:sqlite:" + decryptedFile.getPath());
    final ChannelsDb db = new SqliteChannelsDb(decryptedFileConn);
    final Seq<HasCommitments> commitments = db.listChannels();
    final int channelsCount = commitments.size();
    final List<Long> indexes = new ArrayList<>();
    final Iterator<HasCommitments> commitmentsIt = commitments.iterator();
    while (commitmentsIt.hasNext()) {
      final HasCommitments hc = commitmentsIt.next();
      indexes.add(Math.max(hc.commitments().localCommit().index(), hc.commitments().remoteCommit().index()));
    }
    db.close();
    log.info("found {} channels in backup file from {}", channelsCount, type);
    return new BackupScan(type, indexes, channelsCount, modified, decryptedFile);
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

  private static class BackupScan {
    public final BackupTypes type;
    public final List<Long> commitmentCounts;
    public final int channelsCount;
    public final Date lastModified;
    public final File file;
    private boolean isFromDevice = true;

    public BackupScan(final BackupTypes type, final List<Long> commitmentCounts, int channelsCount, final Date lastModified, final File file) {
      this.type = type;
      this.commitmentCounts = commitmentCounts;
      this.channelsCount = channelsCount;
      this.lastModified = lastModified;
      this.file = file;
    }

    public void setIsFromDevice(final boolean isFromDevice) {
      this.isFromDevice = isFromDevice;
    }
  }
}
