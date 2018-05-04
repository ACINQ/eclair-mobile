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
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import fr.acinq.bitcoin.MnemonicCode;
import fr.acinq.eclair.blockchain.electrum.ElectrumWallet;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityCreateWalletRecoveryBinding;
import fr.acinq.eclair.wallet.utils.Constants;
import scala.collection.JavaConverters;

public class CreateWalletRecoveryActivity extends EclairActivity implements EclairActivity.EncryptSeedCallback {

  private static final String TAG = "CreateRecovery";
  List<Integer> recoveryPositions = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23);
  private List<String> mnemonics = null;

  private ActivityCreateWalletRecoveryBinding mBinding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_create_wallet_recovery);
    try {
      mnemonics = JavaConverters.seqAsJavaListConverter(MnemonicCode.toMnemonics(fr.acinq.eclair.package$.MODULE$.randomBytes(
        ElectrumWallet.SEED_BYTES_LENGTH()).data(), MnemonicCode.englishWordlist())).asJava();
      final int bottomPadding = getResources().getDimensionPixelSize(R.dimen.word_list_padding);
      final int rightPadding = getResources().getDimensionPixelSize(R.dimen.wide_space);
      for (int i = 0; i < mnemonics.size(); i = i + 2) {
        TableRow tr = new TableRow(this);
        tr.setGravity(Gravity.CENTER);
        tr.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
        TextView t1 = new TextView(this);
        t1.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));
        t1.setText(Html.fromHtml(getString(R.string.createrecovery_word_display, i + 1, mnemonics.get(i))));
        t1.setPadding(0, 0, rightPadding, bottomPadding);
        tr.addView(t1);
        TextView t2 = new TextView(this);
        t2.setText(Html.fromHtml(getString(R.string.createrecovery_word_display, i + 2, mnemonics.get(i+1))));
        t2.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));
        t2.setPadding(0, 0, 0, bottomPadding);
        tr.addView(t2);
        mBinding.wordsTable.addView(tr);
      }
    } catch (Exception e) {
      mnemonics = null;
      Log.e(TAG, "could not generate recovery phrase", e);
      Toast.makeText(getApplicationContext(), R.string.createrecovery_generation_failed, Toast.LENGTH_SHORT).show();
      goToStartup();
    }
  }

  private void reset() {
    mBinding.checkInput.setText("");
    mBinding.displayStep.setVisibility(View.VISIBLE);
    mBinding.checkStep.setVisibility(View.GONE);
    mBinding.encryptStep.setVisibility(View.GONE);
  }

  private void goStepCheck() {
    mBinding.displayStep.setVisibility(View.GONE);
    mBinding.checkStep.setVisibility(View.VISIBLE);
    mBinding.encryptStep.setVisibility(View.GONE);
  }

  public void goStepSuccess(View v) {
    mBinding.displayStep.setVisibility(View.GONE);
    mBinding.checkStep.setVisibility(View.GONE);
    mBinding.encryptStep.setVisibility(View.VISIBLE);
    finishCheckRecovery();
  }

  @Override
  protected void onStart() {
    super.onStart();
    reset();
  }

  public void cancel(View view) {
    goToStartup();
  }

  /**
   * Shuffle the words position and initialize the question with the 3 first words in the list of words.
   */
  public void initCheckRecovery(View view) {
    Collections.shuffle(recoveryPositions);
    final List<Integer> pos = getFirst3Positions();
    mBinding.checkQuestion.setText(getString(R.string.createrecovery_check_question, pos.get(0) + 1, pos.get(1) + 1, pos.get(2) + 1));
    goStepCheck();
  }

  private List<Integer> getFirst3Positions() {
    final List<Integer> first3Positions = recoveryPositions.subList(0,3);
    Collections.sort(first3Positions);
    return first3Positions;
  }

  /**
   * Checks if the word belongs to the recovery phrase and is at the right position.
   *
   * @param position position of the word in the recovery phrase
   * @param word     word in the recovery phrase
   * @return false if the check fails
   */
  private boolean checkWordRecoveryPhrase(int position, String word) throws Exception {
    return mnemonics.get(position).equals(word);
  }

  /**
   * Check that the words entered by the user are correct. Proves that the user has made a backup of its list of words.
   */
  public void checkRecovery(View view) {
    mBinding.checkFailed.setVisibility(View.GONE);
    view.clearFocus();
    final InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
    try {
      final String[] userWords = mBinding.checkInput.getText().toString().split(" ");
      final List<Integer> pos = getFirst3Positions();
      if (userWords.length == 3
        && checkWordRecoveryPhrase(pos.get(0), userWords[0])
        && checkWordRecoveryPhrase(pos.get(1), userWords[1])
        && checkWordRecoveryPhrase(pos.get(2), userWords[2])) {
        goStepSuccess(view);
        return;
      }
    } catch (Exception e) {
      mnemonics = null;
      Log.e(TAG, "could not check the recovery phrase", e);
    }
    // check fails
    mBinding.checkFailed.setVisibility(View.VISIBLE);
    reset();
  }

  /**
   * Backup was made. The mnemonics is safe to write to file and can be used by eclair.
   */
  public void finishCheckRecovery() {
    mBinding.writeError.setVisibility(View.GONE);
    if (mnemonics == null) {
      Toast.makeText(this, R.string.createrecovery_general_failure, Toast.LENGTH_SHORT).show();
      goToStartup();
       return;
    }
    final File datadir = new File(getFilesDir(), Constants.ECLAIR_DATADIR);
    final byte[] seed = MnemonicCode.toSeed(JavaConverters.collectionAsScalaIterableConverter(mnemonics).asScala().toSeq(), "").toString().getBytes();
    encryptWallet(this, false, datadir, seed);
  }

  @Override
  public void onEncryptSeedFailure(String message) {
    mBinding.writeError.setText(message);
    mBinding.writeError.setVisibility(View.VISIBLE);
    new Handler().postDelayed(this::finishCheckRecovery, 1400);
  }

  private void goToStartup() {
    Intent startup = new Intent(this, StartupActivity.class);
    startup.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    startup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(startup);
  }

  @Override
  public void onEncryptSeedSuccess() {
    goToStartup();
  }
}
