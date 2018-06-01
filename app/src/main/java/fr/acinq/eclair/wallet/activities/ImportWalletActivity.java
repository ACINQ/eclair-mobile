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

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.google.common.base.Strings;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import fr.acinq.bitcoin.MnemonicCode;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityImportWalletBinding;
import fr.acinq.eclair.wallet.fragments.PinDialog;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import scala.collection.JavaConverters;

public class ImportWalletActivity extends EclairActivity implements EclairActivity.EncryptSeedCallback {

  private static final String TAG = "ImportWallet";
  private ActivityImportWalletBinding mBinding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_import_wallet);
  }

  private void reset() {
    mBinding.importError.setText("");
    mBinding.importError.setVisibility(View.GONE);
  }

  private void showError(String message) {
    mBinding.importError.setText(message);
    mBinding.importError.setVisibility(View.VISIBLE);
  }

  public void cancel(View view) {
    goToStartup();
  }

  private void goToStartup() {
    Intent startup = new Intent(this, StartupActivity.class);
    startup.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    startup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(startup);
  }

  private boolean validateMnemonics(String mnemonics) {
    try {
      MnemonicCode.validate(mnemonics);
      return true;
    }
    catch(Exception e) {
      showError(e.getMessage());
      return false;
    }
  }

  public void importMnemonics(View view) {
    reset();
    final String phrase = mBinding.mnemonicsInput.getText().toString().trim();
    if (validateMnemonics(phrase)) {
      final File datadir = new File(getFilesDir(), Constants.ECLAIR_DATADIR);
      final byte[] seed = MnemonicCode.toSeed(phrase, "").toString().getBytes();
      encryptWallet(this, false, datadir, seed);
    }
  }

  @Override
  public void onEncryptSeedFailure(String message) {
    showError(message);
  }

  @Override
  public void onEncryptSeedSuccess() {
    goToStartup();
  }

}
