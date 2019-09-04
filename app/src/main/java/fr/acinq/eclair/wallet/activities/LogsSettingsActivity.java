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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.databinding.DataBindingUtil;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityLogsSettingsBinding;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.EclairException;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class LogsSettingsActivity extends EclairActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
  private final Logger log = LoggerFactory.getLogger(LogsSettingsActivity.class);
  private ActivityLogsSettingsBinding mBinding;
  private int papertrailActivationCount = 0;

  @SuppressLint("SetTextI18n")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_logs_settings);
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    if (!prefs.getBoolean(Constants.SETTING_PAPERTRAIL_VISIBLE, false)) {
      toolbar.setClickable(true);
      toolbar.setFocusable(true);
      toolbar.setOnClickListener(v -> {
        papertrailActivationCount++;
        if (papertrailActivationCount == 20) {
          PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit()
            .putBoolean(Constants.SETTING_PAPERTRAIL_VISIBLE, true).apply();
          Toast.makeText(this, "unlocked papertrail", Toast.LENGTH_SHORT).show();
        }
      });
    }
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);

    mBinding.setLogsOutputMode(prefs.getString(Constants.SETTING_LOGS_OUTPUT, Constants.LOGS_OUTPUT_LOCAL));
    mBinding.papertrailHostInput.setText(prefs.getString(Constants.SETTING_PAPERTRAIL_HOST, ""));
    mBinding.papertrailPortInput.setText(Integer.toString(prefs.getInt(Constants.SETTING_PAPERTRAIL_PORT, 12345)));
    mBinding.setShowPapertrail(prefs.getBoolean(Constants.SETTING_PAPERTRAIL_VISIBLE, false));
    mBinding.localDirectoryView.setText(getString(R.string.logging_local_directory, getApplicationContext().getExternalFilesDir(Constants.LOGS_DIR)));
    refreshRadioDisplays(prefs);
  }

  @Override
  protected void onResume() {
    super.onResume();
    PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
  }

  @Override
  public void onPause() {
    super.onPause();
    PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
  }

  public void saveOutput(final View view) {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    try {
      if (mBinding.radioLocal.isChecked()) {
        WalletUtils.setupLocalLogging(getApplicationContext());
        prefs.edit().putString(Constants.SETTING_LOGS_OUTPUT, Constants.LOGS_OUTPUT_LOCAL).apply();
      } else if (mBinding.radioPapertrail.isChecked()) {
        final String inputPapertrailHost = mBinding.papertrailHostInput.getText().toString();
        final int inputPapertrailPort = Integer.parseInt(mBinding.papertrailPortInput.getText().toString());
        WalletUtils.setupPapertrailLogging(inputPapertrailHost, inputPapertrailPort);
        prefs.edit()
          .putString(Constants.SETTING_LOGS_OUTPUT, Constants.LOGS_OUTPUT_PAPERTRAIL)
          .putString(Constants.SETTING_PAPERTRAIL_HOST, inputPapertrailHost)
          .putInt(Constants.SETTING_PAPERTRAIL_PORT, inputPapertrailPort)
          .apply();
      } else {
        WalletUtils.disableLogging();
        prefs.edit().putString(Constants.SETTING_LOGS_OUTPUT, Constants.LOGS_OUTPUT_NONE).apply();
      }
      Toast.makeText(this, R.string.logging_toast_update_success, Toast.LENGTH_SHORT).show();
    } catch (EclairException.ExternalStorageUnavailableException e) {
      Toast.makeText(this, R.string.logging_toast_error_external_storage, Toast.LENGTH_SHORT).show();
    } catch (Throwable t) {
      log.error("could not update logs settings", t);
      Toast.makeText(this, R.string.logging_toast_error_generic, Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
    if (Constants.SETTING_LOGS_OUTPUT.equals(key)) {
      refreshRadioDisplays(prefs);
    } else if (Constants.SETTING_PAPERTRAIL_VISIBLE.equals(key)) {
      mBinding.setShowPapertrail(prefs.getBoolean(Constants.SETTING_PAPERTRAIL_VISIBLE, false));
    }
  }

  private void refreshRadioDisplays(final SharedPreferences prefs) {
    final String outputMode = prefs.getString(Constants.SETTING_LOGS_OUTPUT, Constants.LOGS_OUTPUT_LOCAL);
    // disabled
    mBinding.radioNone.setChecked(Constants.LOGS_OUTPUT_NONE.equals(outputMode));
    mBinding.disabledLabel.setText((Constants.LOGS_OUTPUT_NONE.equals(outputMode)
      ? getString(R.string.logging_current_label, getString(R.string.logging_disabled_label))
      : getString(R.string.logging_disabled_label)));
    // local
    mBinding.radioLocal.setChecked(Constants.LOGS_OUTPUT_LOCAL.equals(outputMode));
    mBinding.localLabel.setText((Constants.LOGS_OUTPUT_LOCAL.equals(outputMode)
      ? getString(R.string.logging_current_label, getString(R.string.logging_local_label))
      : getString(R.string.logging_local_label)));
    // papertrail
    mBinding.radioPapertrail.setChecked(Constants.LOGS_OUTPUT_PAPERTRAIL.equals(outputMode));
    mBinding.papertrailLabel.setText((Constants.LOGS_OUTPUT_PAPERTRAIL.equals(outputMode)
      ? getString(R.string.logging_current_label, getString(R.string.logging_papertrailapp_label))
      : getString(R.string.logging_papertrailapp_label)));
  }

  public void handleNoneRadioClick(final View view) {
    mBinding.setLogsOutputMode(Constants.LOGS_OUTPUT_NONE);
  }

  public void handleLocalRadioClick(final View view) {
    mBinding.setLogsOutputMode(Constants.LOGS_OUTPUT_LOCAL);
  }

  public void handlePapertrailRadioClick(final View view) {
    mBinding.setLogsOutputMode(Constants.LOGS_OUTPUT_PAPERTRAIL);
  }

  public void viewLocalLogs(final View view) {
    final Uri uri = WalletUtils.getLastLocalLogFileUri(getApplicationContext());
    if (uri != null) {
      final Intent viewIntent = new Intent(Intent.ACTION_VIEW);
      viewIntent.setDataAndType(uri, "text/plain").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      final Intent externalAppIntent = Intent.createChooser(viewIntent, getString(R.string.logging_external_app_intent));
      startActivity(externalAppIntent);
    } else {
      Toast.makeText(this, R.string.logging_toast_error_file_not_found, Toast.LENGTH_SHORT).show();
    }
  }

  public void shareLocalLogs(final View view) {
    final Uri uri = WalletUtils.getLastLocalLogFileUri(getApplicationContext());
    if (uri != null) {
      final Intent shareIntent = new Intent(Intent.ACTION_SEND);
      shareIntent.setType("text/plain");
      shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
      shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.logging_local_share_logs_subject));
      startActivity(Intent.createChooser(shareIntent, getString(R.string.logging_local_share_logs_title)));
    }
  }


}
