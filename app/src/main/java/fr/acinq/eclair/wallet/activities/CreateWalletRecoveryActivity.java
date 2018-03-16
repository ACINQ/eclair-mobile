package fr.acinq.eclair.wallet.activities;

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.google.common.base.Joiner;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import fr.acinq.bitcoin.MnemonicCode;
import fr.acinq.eclair.blockchain.electrum.ElectrumWallet;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityCreateWalletRecoveryBinding;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import scala.collection.JavaConverters;

public class CreateWalletRecoveryActivity extends AppCompatActivity {

  private static final String TAG = "CreateRecovery";
  List<Integer> recoveryPositions = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23);
  private List<String> mnemonics = null;

  private ActivityCreateWalletRecoveryBinding mBinding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_create_wallet_recovery);
  }

  @Override
  protected void onStop() {
    mnemonics = null;
    super.onStop();
  }

  private void reset() {
    mBinding.checkInput.setText("");
    mBinding.displayStep.setVisibility(View.VISIBLE);
    mBinding.checkStep.setVisibility(View.GONE);
    mBinding.successStep.setVisibility(View.GONE);
  }

  private void goStepCheck() {
    mBinding.displayStep.setVisibility(View.GONE);
    mBinding.checkStep.setVisibility(View.VISIBLE);
    mBinding.successStep.setVisibility(View.GONE);
  }

  private void goStepSuccess() {
    mBinding.displayStep.setVisibility(View.GONE);
    mBinding.checkStep.setVisibility(View.GONE);
    mBinding.successStep.setVisibility(View.VISIBLE);
  }

  private void showWriteError(String message) {
    mBinding.writeError.setText(message);
    mBinding.writeError.setVisibility(View.VISIBLE);
  }

  @Override
  protected void onStart() {
    super.onStart();
    reset();
    try {
      mnemonics = JavaConverters.seqAsJavaListConverter(MnemonicCode.toMnemonics(fr.acinq.eclair.package$.MODULE$.randomBytes(
        ElectrumWallet.SEED_BYTES_LENGTH()).data(), MnemonicCode.englishWordlist())).asJava();
      mBinding.entropyDisplay.setText(Joiner.on(" ").join(mnemonics));
    } catch (Exception e) {
      mnemonics = null;
      Log.e(TAG, "could not generate recovery phrase", e);
      mBinding.entropyDisplay.setText(getString(R.string.createrecovery_generation_failed));
      mBinding.gotoCheck.setVisibility(View.GONE);
    }
  }

  public void cancel(View view) {
    startActivity(new Intent(getBaseContext(), StartupActivity.class));
  }

  /**
   * Shuffle the words position and initialize the question with the 3 first words in the list of words.
   *
   * @param view
   */
  public void initCheckRecovery(View view) {
    Collections.shuffle(recoveryPositions);
    mBinding.checkQuestion.setText(getString(R.string.createrecovery_check_question,
      recoveryPositions.get(0) + 1, recoveryPositions.get(1) + 1, recoveryPositions.get(2) + 1));
    goStepCheck();
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
   *
   * @param view
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
      if (userWords.length == 3
        && checkWordRecoveryPhrase(recoveryPositions.get(0), userWords[0])
        && checkWordRecoveryPhrase(recoveryPositions.get(1), userWords[1])
        && checkWordRecoveryPhrase(recoveryPositions.get(2), userWords[2])) {
        goStepSuccess();
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
   *
   * @param view
   */
  public void finishCheckRecovery(View view) {
    if (mnemonics == null) {
      showWriteError("Recovery phrase is not valid.");
      return;
    }
    final File datadir = new File(getFilesDir(), Constants.ECLAIR_DATADIR);
    try {
      WalletUtils.writeSeedFile(datadir, mnemonics);
      startActivity(new Intent(getBaseContext(), StartupActivity.class));
    } catch (Exception e) {
      showWriteError("Could not write seed to disk");
    }
  }
}
