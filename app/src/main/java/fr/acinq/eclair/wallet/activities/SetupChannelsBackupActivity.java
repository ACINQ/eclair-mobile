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

import androidx.databinding.DataBindingUtil;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;

import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivitySetupChannelsBackupBinding;
import fr.acinq.eclair.wallet.utils.BackupHelper;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.LocalBackupHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetupChannelsBackupActivity extends ChannelsBackupBaseActivity {

  private final Logger log = LoggerFactory.getLogger(SetupChannelsBackupActivity.class);
  public final static String EXTRA_SETUP_IGNORE_GDRIVE_BACKUP = BuildConfig.APPLICATION_ID + ".SETUP_IGNORE_GDRIVE_BACKUP";
  private ActivitySetupChannelsBackupBinding mBinding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_setup_channels_backup);
    mBinding.submitButton.setOnClickListener(v -> {
      mBinding.setSetupBackupStep(Constants.SETUP_BACKUP_REQUESTING_ACCESS);
      requestAccess(true, mBinding.requestGdriveAccessCheckbox.isChecked());
    });
    mBinding.requestLocalAccessCheckbox.setChecked(true);
    mBinding.setSetupBackupStep(Constants.SETUP_BACKUP_INIT);

    // only show gdrive box if necessary
    if (!getIntent().getBooleanExtra(EXTRA_SETUP_IGNORE_GDRIVE_BACKUP, false) && BackupHelper.GoogleDrive.isGDriveAvailable(getApplicationContext())) {
      mBinding.requestGdriveAccessCheckbox.setVisibility(View.VISIBLE);
    } else {
      mBinding.requestGdriveAccessCheckbox.setEnabled(false);
      mBinding.requestGdriveAccessCheckbox.setChecked(false);
      mBinding.requestGdriveAccessCheckbox.setVisibility(View.GONE);
    }
  }

  @Override
  protected void applyGdriveAccessDenied() {
    super.applyGdriveAccessDenied();
    BackupHelper.GoogleDrive.disableGDriveBackup(getApplicationContext());
    mBinding.requestGdriveAccessCheckbox.setChecked(false);
  }

  @Override
  protected void applyGdriveAccessGranted(final GoogleSignInAccount signIn) {
    super.applyGdriveAccessGranted(signIn);
    BackupHelper.GoogleDrive.enableGDriveBackup(getApplicationContext());
    mBinding.requestGdriveAccessCheckbox.setChecked(true);
  }

  @Override
  protected void applyAccessRequestDone() {
    if (!LocalBackupHelper.INSTANCE.hasLocalAccess(getApplicationContext())) {
      log.info("access to local drive denied!");
      mBinding.setSetupBackupStep(Constants.SETUP_BACKUP_INIT);
      Toast.makeText(this, getString(R.string.setupbackup_local_required), Toast.LENGTH_LONG).show();
    } else {
      finish();
    }
  }

  @Override
  protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == GDRIVE_REQUEST_CODE_SIGN_IN) {
      handleGdriveSigninResult(data);
    }
  }

  private void handleGdriveSigninResult(final Intent data) {
    try {
      final GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class);
      if (account == null) {
        throw new RuntimeException("empty account");
      }
      applyGdriveAccessGranted(account);
    } catch (Exception e) {
      log.error("Google Drive sign-in failed, could not get account: ", e);
      Toast.makeText(this, "Sign-in failed.", Toast.LENGTH_SHORT).show();
      applyGdriveAccessDenied();
    }
  }

}
