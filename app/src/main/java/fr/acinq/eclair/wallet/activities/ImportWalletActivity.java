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
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import java.io.File;

import fr.acinq.bitcoin.MnemonicCode;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityImportWalletBinding;
import fr.acinq.eclair.wallet.utils.Constants;

public class ImportWalletActivity extends EclairActivity implements EclairActivity.EncryptSeedCallback {

  private ActivityImportWalletBinding mBinding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_import_wallet);
    goToInit();
  }

  private void goToInit() {
    mBinding.setImportStep(Constants.IMPORT_WALLET_STEP_INIT);
  }

  private void showError(final String message) {
    mBinding.setImportStep(Constants.IMPORT_WALLET_STEP_ERROR);
    mBinding.importError.setText(message);
  }

  public void cancel(View view) {
    goToStartup();
  }

  private void goToStartup() {
    final Intent startup = new Intent(this, StartupActivity.class);
    startup.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    startup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(startup);
  }

  public void importMnemonics(final View view) {
    try {
      final String mnemonics = mBinding.mnemonicsInput.getText().toString().trim();
      MnemonicCode.validate(mnemonics);
      final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      if (imm != null && view != null && view.getWindowToken() != null) {
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
      }
      mBinding.setImportStep(Constants.IMPORT_WALLET_STEP_PASSPHRASE);
    } catch (Exception e) {
      showError(getString(R.string.importwallet_error, e.getLocalizedMessage()));
      new Handler().postDelayed(this::goToInit, 2800);
    }
  }

  public void importPassphrase(final View view) {
    try {
      final String mnemonics = mBinding.mnemonicsInput.getText().toString().trim();
      final String passphrase = mBinding.passphraseInput.getText().toString();
      MnemonicCode.toSeed(mnemonics, passphrase).toString().getBytes();
      final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      if (imm != null && view != null && view.getWindowToken() != null) {
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
      }
      mBinding.setImportStep(Constants.IMPORT_WALLET_STEP_ENCRYPT);
    } catch (Exception e) {
      showError(getString(R.string.importwallet_error, e.getLocalizedMessage()));
      new Handler().postDelayed(() -> mBinding.setImportStep(Constants.IMPORT_WALLET_STEP_PASSPHRASE), 2800);
    }
  }

  public void encryptSeed(final View view) {
    try {
      final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      if (imm != null && view != null && view.getWindowToken() != null) {
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
      }
      final String mnemonics = mBinding.mnemonicsInput.getText().toString().trim();
      final String passphrase = mBinding.passphraseInput.getText().toString();
      final File datadir = new File(getFilesDir(), Constants.ECLAIR_DATADIR);
      final byte[] seed = MnemonicCode.toSeed(mnemonics, passphrase).toString().getBytes();
      encryptWallet(this, false, datadir, seed);
    } catch (Exception e) {
      showError(getString(R.string.createwallet_error_write_seed, e.getLocalizedMessage()));
      new Handler().postDelayed(() -> mBinding.setImportStep(Constants.IMPORT_WALLET_STEP_INIT), 2800);
    }
  }

  @Override
  public void onEncryptSeedFailure(String message) {
    showError(message);
    new Handler().postDelayed(() -> mBinding.setImportStep(Constants.IMPORT_WALLET_STEP_ENCRYPT), 2500);
  }

  @Override
  public void onEncryptSeedSuccess() {
    mBinding.setImportStep(Constants.IMPORT_WALLET_STEP_SUCCESS);
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    prefs.edit().putInt(Constants.SETTING_WALLET_ORIGIN, Constants.WALLET_ORIGIN_RESTORED_FROM_SEED).apply();
    new Handler().postDelayed(this::goToStartup, 1700);
  }

}
