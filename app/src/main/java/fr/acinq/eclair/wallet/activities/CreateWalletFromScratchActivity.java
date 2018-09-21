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
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TableRow;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import fr.acinq.bitcoin.MnemonicCode;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityCreateWalletFromScratchBinding;
import fr.acinq.eclair.wallet.utils.Constants;
import scala.collection.JavaConverters;

public class CreateWalletFromScratchActivity extends EclairActivity implements EclairActivity.EncryptSeedCallback {

  private final Logger log = LoggerFactory.getLogger(CreateWalletFromScratchActivity.class);

  final List<Integer> recoveryPositions = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
  private List<String> mnemonics = null;
  private ActivityCreateWalletFromScratchBinding mBinding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_create_wallet_from_scratch);
    try {
      mnemonics = JavaConverters.seqAsJavaListConverter(MnemonicCode.toMnemonics(
        fr.acinq.eclair.package$.MODULE$.randomBytes(16).data(),
        MnemonicCode.englishWordlist())).asJava();
      final int bottomPadding = getResources().getDimensionPixelSize(R.dimen.word_list_padding);
      final int rightPadding = getResources().getDimensionPixelSize(R.dimen.space_lg);
      for (int i = 0; i < mnemonics.size() / 2; i = i + 1) {
        TableRow tr = new TableRow(this);
        tr.setGravity(Gravity.CENTER);
        tr.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
        TextView t1 = new TextView(this);
        t1.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));
        t1.setText(Html.fromHtml(getString(R.string.createwallet_single_word_display, i + 1, mnemonics.get(i))));
        t1.setPadding(0, 0, rightPadding, bottomPadding);
        tr.addView(t1);
        TextView t2 = new TextView(this);
        t2.setText(Html.fromHtml(getString(R.string.createwallet_single_word_display, i + (mnemonics.size() / 2) + 1, mnemonics.get(i + (mnemonics.size() / 2)))));
        t2.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));
        t2.setPadding(0, 0, 0, bottomPadding);
        tr.addView(t2);
        mBinding.wordsTable.addView(tr);
      }
      goToInit();
    } catch (Exception e) {
      mnemonics = null;
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (mnemonics == null) {
      log.error("mnemonics is null");
      showError(getString(R.string.createwallet_error_seed_generation));
      new Handler().postDelayed(this::goToStartup, 1400);
    }
  }

  private void showError(final String message) {
    mBinding.setCreationStep(Constants.CREATE_WALLET_STEP_ERROR);
    mBinding.errorView.setText(message);
  }

  private void goToInit() {
    mBinding.setCreationStep(Constants.CREATE_WALLET_STEP_INIT);
  }

  public void goToVerificationStep(View view) {
    Collections.shuffle(recoveryPositions);
    final List<Integer> pos = getFirst3Positions();
    mBinding.checkQuestion.setText(getString(R.string.createwallet_check_title, pos.get(0) + 1, pos.get(1) + 1, pos.get(2) + 1));
    mBinding.checkInput1Hint.setHint(getString(R.string.createwallet_check_input_hint, pos.get(0) + 1));
    mBinding.checkInput1.setText("");
    mBinding.checkInput2Hint.setHint(getString(R.string.createwallet_check_input_hint, pos.get(1) + 1));
    mBinding.checkInput2.setText("");
    mBinding.checkInput3Hint.setHint(getString(R.string.createwallet_check_input_hint, pos.get(2) + 1));
    mBinding.checkInput3.setText("");
    mBinding.setCreationStep(Constants.CREATE_WALLET_STEP_VERIFICATION);
  }

  /**
   * Check that the user has correctly backed up the mnemonics.
   */
  public void verifyUserBackup(View view) {
    view.clearFocus();
    final InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
    try {
      final String userWord1 = mBinding.checkInput1.getText().toString().trim();
      final String userWord2 = mBinding.checkInput2.getText().toString().trim();
      final String userWord3 = mBinding.checkInput3.getText().toString().trim();
      final List<Integer> pos = getFirst3Positions();
      if (checkWordRecoveryPhrase(pos.get(0), userWord1)
        && checkWordRecoveryPhrase(pos.get(1), userWord2)
        && checkWordRecoveryPhrase(pos.get(2), userWord3)) {
        goToEncryptionStep(view);
      } else {
        throw new RuntimeException();
      }
    } catch (Exception e) {
      mBinding.setCreationStep(Constants.CREATE_WALLET_STEP_CHECK_FAILED);
      new Handler().postDelayed(this::goToInit, 3000);
    }
  }

  public void goToEncryptionStep(View view) {
    if (mnemonics == null) {
      showError(getString(R.string.createwallet_error_generic));
      new Handler().postDelayed(this::goToStartup, 1400);
    } else {
      mBinding.setCreationStep(Constants.CREATE_WALLET_STEP_ENCRYPTION);
      new Handler().postDelayed(() -> {
        final File datadir = new File(getFilesDir(), Constants.ECLAIR_DATADIR);
        final byte[] seed = MnemonicCode.toSeed(JavaConverters.collectionAsScalaIterableConverter(mnemonics).asScala().toSeq(), "").toString().getBytes();
        encryptWallet(this, false, datadir, seed);
      }, 1500);
    }
  }

  public void cancel(View view) {
    goToStartup();
  }

  private List<Integer> getFirst3Positions() {
    final List<Integer> first3Positions = recoveryPositions.subList(0, 3);
    Collections.sort(first3Positions);
    return first3Positions;
  }

  /**
   * Checks if the word belongs to the recovery phrase and is at the right position.
   *
   * @param position position of the word in the recovery phrase
   * @param word     word entered by the user
   * @return false if the check fails
   */
  private boolean checkWordRecoveryPhrase(final int position, final String word) {
    return mnemonics.get(position).equalsIgnoreCase(word);
  }

  @Override
  public void onEncryptSeedFailure(final String message) {
    showError(message);
    new Handler().postDelayed(() -> goToEncryptionStep(null), 1400);
  }

  private void goToStartup() {
    Intent startup = new Intent(this, StartupActivity.class);
    startup.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    startup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(startup);
  }

  @Override
  public void onEncryptSeedSuccess() {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    prefs.edit().putInt(Constants.SETTING_WALLET_ORIGIN, Constants.WALLET_ORIGIN_FROM_SCRATCH).apply();
    mBinding.setCreationStep(Constants.CREATE_WALLET_STEP_COMPLETE);
    new Handler().postDelayed(this::goToStartup, 900);
  }
}
