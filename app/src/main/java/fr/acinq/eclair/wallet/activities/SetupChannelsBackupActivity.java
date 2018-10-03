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

import android.app.Dialog;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import androidx.work.ExistingWorkPolicy;
import androidx.work.WorkManager;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivitySetupChannelsBackupBinding;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class SetupChannelsBackupActivity extends GoogleDriveBaseActivity {

  private ActivitySetupChannelsBackupBinding mBinding;
  private Dialog backupAbout;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_setup_channels_backup);
    backupAbout = getCustomDialog(R.string.backup_about).setPositiveButton(R.string.btn_ok, null).create();
  }

  public void grantAccess(final View view) {
    new Thread() {
      @Override
      public void run() {
        initOrSignInGoogleDrive();
      }
    }.start();
  }

  public void showDetails(final View view) {
    if (backupAbout != null) backupAbout.show();
  }

  public void skipBackupSetup(final View view) {
    PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit()
      .putBoolean(Constants.SETTING_CHANNELS_BACKUP_SEEN_ONCE, true).apply();
    finish();
  }

  @Override
  void applyAccessDenied() {
    PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit()
      .putBoolean(Constants.SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_ENABLED, false).apply();
  }

  @Override
  void applyAccessGranted(final GoogleSignInAccount signIn) {
    PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit()
      .putBoolean(Constants.SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_ENABLED, true)
      .putBoolean(Constants.SETTING_CHANNELS_BACKUP_SEEN_ONCE, true).apply();
    WorkManager.getInstance()
      .beginUniqueWork("ChannelsBackup", ExistingWorkPolicy.REPLACE,
        WalletUtils.generateBackupRequest(app.seedHash.get(), app.backupKey_v2.get()))
      .enqueue();
    finish();
  }

  @Override
  void onDriveClientReady(final GoogleSignInAccount signInAccount) {
    applyAccessGranted(signInAccount);
  }
}
