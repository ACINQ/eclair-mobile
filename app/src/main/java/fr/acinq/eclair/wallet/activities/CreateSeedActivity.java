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
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.transition.TransitionManager;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import fr.acinq.bitcoin.MnemonicCode;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.adapters.SimplePagerAdapter;
import fr.acinq.eclair.wallet.databinding.ActivityCreateSeedBinding;
import fr.acinq.eclair.wallet.fragments.WalletCheckWordsFragment;
import fr.acinq.eclair.wallet.fragments.WalletCreateSeedFragment;
import fr.acinq.eclair.wallet.fragments.WalletEncryptFragment;
import fr.acinq.eclair.wallet.fragments.WalletPassphraseConfirmFragment;
import fr.acinq.eclair.wallet.fragments.WalletPassphraseFragment;
import fr.acinq.eclair.wallet.utils.Constants;
import scala.collection.JavaConverters;

public class CreateSeedActivity extends EclairActivity implements EclairActivity.EncryptSeedCallback {

  final List<Integer> recoveryPositions = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
  private final Logger log = LoggerFactory.getLogger(CreateSeedActivity.class);
  private List<String> mMnemonics = null;
  private String mPassphrase = "";
  private boolean mMnemonicsVerified = false;
  private WalletCreateSeedFragment mWalletCreateSeedFragment;
  private WalletCheckWordsFragment mWalletCheckWordsFragment;
  private WalletPassphraseFragment mWalletPassphraseFragment;
  private WalletPassphraseConfirmFragment mWalletPassphraseConfirmFragment;
  private WalletEncryptFragment mWalletEncryptFragment;
  private ActivityCreateSeedBinding mBinding;
  private Animation mErrorAnimation;
  final Handler verifHandler = new Handler();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_create_seed);

    try {
      mMnemonics = JavaConverters.seqAsJavaListConverter(MnemonicCode.toMnemonics(
        fr.acinq.eclair.package$.MODULE$.randomBytes(16).data(),
        MnemonicCode.englishWordlist())).asJava();

      final Bundle args = new Bundle();
      args.putStringArrayList(WalletCreateSeedFragment.BUNDLE_ARG_MNEMONICS, new ArrayList<>(mMnemonics));
      final List<Fragment> fragments = new ArrayList<>();
      mWalletCreateSeedFragment = new WalletCreateSeedFragment();
      mWalletCreateSeedFragment.setArguments(args);
      mWalletCheckWordsFragment = new WalletCheckWordsFragment();
      mWalletPassphraseFragment = new WalletPassphraseFragment();
      mWalletPassphraseConfirmFragment = new WalletPassphraseConfirmFragment();
      mWalletEncryptFragment = new WalletEncryptFragment();
      fragments.add(mWalletCreateSeedFragment);
      fragments.add(mWalletCheckWordsFragment);
      fragments.add(mWalletPassphraseFragment);
      fragments.add(mWalletPassphraseConfirmFragment);
      fragments.add(mWalletEncryptFragment);
      final SimplePagerAdapter pagerAdapter = new SimplePagerAdapter(getSupportFragmentManager(), fragments);
      mBinding.viewPager.setAdapter(pagerAdapter);
      mBinding.viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
        @Override
        public void onPageSelected(int position) {
          super.onPageSelected(position);
          mBinding.setCreationStep(position);
        }
      });
      goToInit(null);

    } catch (Exception e) {
      mMnemonics = null;
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    mErrorAnimation = AnimationUtils.loadAnimation(this, R.anim.shake);
  }

  protected void onResume() {
    super.onResume();
    if (mMnemonics == null) {
      log.error("mnemonics is null");
      mBinding.error.setText(R.string.createwallet_error_seed_generation);
      mBinding.setCreationStep(Constants.SEED_SPAWN_ERROR);
      new Handler().postDelayed(() -> goToStartup(), 1500);
    }
  }

  @Override
  public void onBackPressed() {
    switch (mBinding.viewPager.getCurrentItem()) {
      case 0: // init -> finish + startup page
        goToStartup();
        break;
      case 1: // check -> init
        goToInit(null);
        break;
      case 2: // passphrase -> init
        goToInit(null);
        break;
      case 3: // confirm passphrase -> passphrase
        goToPassphraseStep(null);
        break;
      case 4: // encrypting -> passphrase
        goToPassphraseStep(null);
        break;
      default:
        break;
    }
  }

  public void goToInit(final View view) {
    mBinding.viewPager.setCurrentItem(0);
  }

  public void goToVerificationStep(final View view) {
    verifHandler.removeCallbacks(null);
    mWalletCheckWordsFragment.mBinding.verificationError.setVisibility(View.GONE);
    mWalletCheckWordsFragment.mBinding.inputGrid.clearAnimation();
    if (mMnemonicsVerified) {
      goToPassphraseStep(null);
    } else {
      setUpCheckWords();
      mBinding.viewPager.setCurrentItem(1);
    }
  }

  private void setUpCheckWords() {
    Collections.shuffle(recoveryPositions);
    final List<Integer> pos = getFirst3Positions();
    mWalletCheckWordsFragment.mBinding.checkQuestion.setText(getString(R.string.createwallet_check_instructions, pos.get(0) + 1, pos.get(1) + 1, pos.get(2) + 1));
    mWalletCheckWordsFragment.mBinding.checkInput1Hint.setText(getString(R.string.createwallet_check_input_hint, pos.get(0) + 1));
    mWalletCheckWordsFragment.mBinding.checkInput1.setText("");
    mWalletCheckWordsFragment.mBinding.checkInput2Hint.setText(getString(R.string.createwallet_check_input_hint, pos.get(1) + 1));
    mWalletCheckWordsFragment.mBinding.checkInput2.setText("");
    mWalletCheckWordsFragment.mBinding.checkInput3Hint.setText(getString(R.string.createwallet_check_input_hint, pos.get(2) + 1));
    mWalletCheckWordsFragment.mBinding.checkInput3.setText("");
  }

  /**
   * Check that the user has correctly backed up the mMnemonics.
   */
  public void verifyUserBackup(View view) {
    verifHandler.removeCallbacks(null);
    mWalletCheckWordsFragment.mBinding.inputGrid.clearAnimation();
    mWalletCheckWordsFragment.mBinding.verificationError.setVisibility(View.GONE);
    view.clearFocus();
    final InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
    try {
      final String userWord1 = mWalletCheckWordsFragment.mBinding.checkInput1.getText().toString().trim();
      final String userWord2 = mWalletCheckWordsFragment.mBinding.checkInput2.getText().toString().trim();
      final String userWord3 = mWalletCheckWordsFragment.mBinding.checkInput3.getText().toString().trim();
      final List<Integer> pos = getFirst3Positions();
      if (checkWordRecoveryPhrase(pos.get(0), userWord1)
        && checkWordRecoveryPhrase(pos.get(1), userWord2)
        && checkWordRecoveryPhrase(pos.get(2), userWord3)) {
        mMnemonicsVerified = true;
        goToPassphraseStep(view);
      } else {
        throw new RuntimeException();
      }
    } catch (Exception e) {
      TransitionManager.beginDelayedTransition(mWalletCheckWordsFragment.mBinding.transitionsLayout);
      mWalletCheckWordsFragment.mBinding.inputGrid.startAnimation(mErrorAnimation);
      mWalletCheckWordsFragment.mBinding.verificationError.setVisibility(View.VISIBLE);
      verifHandler.postDelayed(() -> {
        setUpCheckWords();
        TransitionManager.beginDelayedTransition(mWalletCheckWordsFragment.mBinding.transitionsLayout);
        mWalletCheckWordsFragment.mBinding.verificationError.setVisibility(View.GONE);
      }, 2000);
      view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
      mMnemonicsVerified = false;
    }
  }

  public void goToPassphraseStep(final View view) {
    mBinding.viewPager.setCurrentItem(2);
  }

  public void goToPassphraseConfirmStep(final View view) {
    TransitionManager.beginDelayedTransition(mWalletPassphraseFragment.mBinding.transitionsLayout);
    mWalletPassphraseFragment.mBinding.passphraseError.setVisibility(View.GONE);
    mWalletPassphraseConfirmFragment.mBinding.passphraseConfirmInput.setText("");
    final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null && view != null && view.getWindowToken() != null) {
      imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
    if ("".equals(mWalletPassphraseFragment.mBinding.passphraseInput.getText().toString())) {
      goToEncryptionStep(null);
    } else {
      mBinding.viewPager.setCurrentItem(3);
    }
  }

  public void goToEncryptionStep(final View view) {
    final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null && view != null && view.getWindowToken() != null) {
      imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
    final String passphrase = mWalletPassphraseFragment.mBinding.passphraseInput.getText().toString();
    final String passphraseConfirm = mWalletPassphraseConfirmFragment.mBinding.passphraseConfirmInput.getText().toString();
    if (passphrase.equals(passphraseConfirm)) {
      mBinding.viewPager.setCurrentItem(4);
      mPassphrase = passphrase;
    } else {
      goToPassphraseStep(view);
      TransitionManager.beginDelayedTransition(mWalletPassphraseFragment.mBinding.transitionsLayout);
      mWalletPassphraseFragment.mBinding.passphraseError.setVisibility(View.VISIBLE);
      mWalletPassphraseFragment.mBinding.passphraseInput.startAnimation(mErrorAnimation);
    }
  }

  public void encryptSeed(final View view) {
    TransitionManager.beginDelayedTransition(mWalletPassphraseFragment.mBinding.transitionsLayout);
    mWalletEncryptFragment.mBinding.encryptionError.setVisibility(View.GONE);
    mBinding.setCreationStep(Constants.SEED_SPAWN_ENCRYPTION);
    new Thread() {
      @Override
      public void run() {
        try {
          if (mMnemonics == null) {
            // reference was lost -- cannot continue
            throw new RuntimeException(getString(R.string.createwallet_invalid_seed));
          }
          final String passphrase = mPassphrase;
          final File datadir = new File(getFilesDir(), Constants.ECLAIR_DATADIR);
          final byte[] seed = MnemonicCode.toSeed(JavaConverters.collectionAsScalaIterableConverter(mMnemonics).asScala().toSeq(), passphrase).toString().getBytes();
          runOnUiThread(() -> encryptWallet(CreateSeedActivity.this, false, datadir, seed));
        } catch (Exception e) {
          runOnUiThread(() -> {
            mBinding.error.setText(getString(R.string.createwallet_error_write_seed, e.getLocalizedMessage()));
            mBinding.setCreationStep(Constants.SEED_SPAWN_ERROR);
            new Handler().postDelayed(() -> goToStartup(), 1500);
          });
        }
      }
    }.start();
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
    return mMnemonics.get(position).equalsIgnoreCase(word);
  }

  @Override
  public void onEncryptSeedFailure(final String message) {
    mWalletEncryptFragment.mBinding.encryptionError.setText(message);
    TransitionManager.beginDelayedTransition(mWalletEncryptFragment.mBinding.transitionsLayout);
    mWalletEncryptFragment.mBinding.encryptionError.setVisibility(View.VISIBLE);
    mBinding.setCreationStep(4);
    goToEncryptionStep(null);
  }

  private void goToStartup() {
    Intent startup = new Intent(this, StartupActivity.class);
    startup.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    startup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(startup);
    finish();
  }

  @Override
  public void onEncryptSeedSuccess() {
    mBinding.setCreationStep(Constants.SEED_SPAWN_COMPLETE);
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    prefs.edit().putInt(Constants.SETTING_WALLET_ORIGIN, Constants.WALLET_ORIGIN_FROM_SCRATCH).apply();
    new Handler().postDelayed(this::goToStartup, 1700);
  }
}
