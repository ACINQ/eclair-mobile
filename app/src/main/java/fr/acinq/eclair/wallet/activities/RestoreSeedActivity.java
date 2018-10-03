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
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import fr.acinq.bitcoin.MnemonicCode;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.adapters.SimplePagerAdapter;
import fr.acinq.eclair.wallet.databinding.ActivityRestoreSeedBinding;
import fr.acinq.eclair.wallet.fragments.WalletEncryptFragment;
import fr.acinq.eclair.wallet.fragments.WalletImportSeedFragment;
import fr.acinq.eclair.wallet.fragments.WalletPassphraseFragment;
import fr.acinq.eclair.wallet.utils.Constants;

public class RestoreSeedActivity extends EclairActivity implements EclairActivity.EncryptSeedCallback {

  private ActivityRestoreSeedBinding mBinding;
  private WalletImportSeedFragment mWalletImportSeedFragment;
  private WalletPassphraseFragment mWalletPassphraseFragment;
  private WalletEncryptFragment mWalletEncryptFragment;
  private Animation mErrorAnimation;

  final Handler seedErrorHandler = new Handler();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_restore_seed);

    final List<Fragment> fragments = new ArrayList<>();
    mWalletImportSeedFragment = new WalletImportSeedFragment();
    mWalletPassphraseFragment = new WalletPassphraseFragment();
    mWalletEncryptFragment = new WalletEncryptFragment();
    fragments.add(mWalletImportSeedFragment);
    fragments.add(mWalletPassphraseFragment);
    fragments.add(mWalletEncryptFragment);
    final SimplePagerAdapter pagerAdapter = new SimplePagerAdapter(getSupportFragmentManager(), fragments);
    mBinding.viewPager.setAdapter(pagerAdapter);
    mBinding.viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
      @Override
      public void onPageSelected(int position) {
        super.onPageSelected(position);
        mBinding.setImportStep(position);
      }
    });
    goToInit();
  }

  @Override
  public void onBackPressed() {
    switch (mBinding.viewPager.getCurrentItem()) {
      case 0: // init -> finish + startup page
        goToStartup();
        break;
      case 1: // passphrase -> init
        goToInit();
        break;
      case 2: // encrypting -> passphrase
        importMnemonics(null);
        break;
      default:
        break;
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    mErrorAnimation = AnimationUtils.loadAnimation(this, R.anim.shake);
  }

  private void goToInit() {
    mBinding.viewPager.setCurrentItem(0);
  }

  private void goToPassphrase() {
    mBinding.viewPager.setCurrentItem(1);
  }

  private void goToEncryption() {
    mBinding.viewPager.setCurrentItem(2);
  }

  private void goToStartup() {
    final Intent startup = new Intent(this, StartupActivity.class);
    startup.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    startup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(startup);
    finish();
  }

  public void importMnemonics(final View view) {
    mWalletImportSeedFragment.mBinding.mnemonicsInput.clearAnimation();
    seedErrorHandler.removeCallbacks(null);
    try {
      final String mnemonics = mWalletImportSeedFragment.mBinding.mnemonicsInput.getText().toString().trim();
      MnemonicCode.validate(mnemonics);
      final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      if (imm != null && view != null && view.getWindowToken() != null) {
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
      }
      goToPassphrase();
    } catch (Exception e) {
      handleSeedError(R.string.importwallet_seed_error, e.getLocalizedMessage());
    }
  }

  private void handleSeedError(final int errorCode, final String message) {
    TransitionManager.beginDelayedTransition(mWalletImportSeedFragment.mBinding.transitionsLayout);
    mWalletImportSeedFragment.mBinding.mnemonicsInputLayout.startAnimation(mErrorAnimation);
    mWalletImportSeedFragment.mBinding.seedError.setText(getString(errorCode, message));
    mWalletImportSeedFragment.mBinding.seedError.setVisibility(View.VISIBLE);
    seedErrorHandler.postDelayed(() -> {
      TransitionManager.beginDelayedTransition(mWalletImportSeedFragment.mBinding.transitionsLayout);
      mWalletImportSeedFragment.mBinding.seedError.setVisibility(View.GONE);
    }, 5000);
  }

  public void goToPassphraseConfirmStep(final View view) {
    try {
      final String mnemonics = mWalletImportSeedFragment.mBinding.mnemonicsInput.getText().toString().trim();
      final String passphrase = mWalletPassphraseFragment.mBinding.passphraseInput.getText().toString();
      MnemonicCode.toSeed(mnemonics, passphrase).toString().getBytes();
      final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      if (imm != null && view != null && view.getWindowToken() != null) {
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
      }
      goToEncryption();
    } catch (Exception e) {
      goToInit();
      handleSeedError(R.string.importwallet_error, e.getLocalizedMessage());
    }
  }

  public void encryptSeed(final View view) {
    TransitionManager.beginDelayedTransition(mWalletPassphraseFragment.mBinding.transitionsLayout);
    mWalletEncryptFragment.mBinding.encryptionError.setVisibility(View.GONE);
    mBinding.setImportStep(Constants.SEED_SPAWN_ENCRYPTION);
    new Thread() {
      @Override
      public void run() {
        try {
          final String mnemonics = mWalletImportSeedFragment.mBinding.mnemonicsInput.getText().toString().trim();
          final String passphrase = mWalletPassphraseFragment.mBinding.passphraseInput.getText().toString();
          if (mnemonics.equals("")) {
            // reference was lost -- cannot continue
            throw new RuntimeException(getString(R.string.createwallet_invalid_seed));
          }
          final File datadir = new File(getFilesDir(), Constants.ECLAIR_DATADIR);
          final byte[] seed = MnemonicCode.toSeed(mnemonics, passphrase).toString().getBytes();
          runOnUiThread(() -> encryptWallet(RestoreSeedActivity.this, false, datadir, seed));
        } catch (Exception e) {
          runOnUiThread(() -> {
            mBinding.error.setText(getString(R.string.createwallet_error_write_seed, e.getLocalizedMessage()));
            mBinding.setImportStep(Constants.SEED_SPAWN_ERROR);
            new Handler().postDelayed(() -> goToStartup(), 1500);
          });
        }
      }
    }.start();
  }

  @Override
  public void onEncryptSeedFailure(String message) {
    mBinding.setImportStep(2);
    TransitionManager.beginDelayedTransition(mWalletEncryptFragment.mBinding.transitionsLayout);
    goToEncryption();
    mWalletEncryptFragment.mBinding.encryptionError.setText(message);
    mWalletEncryptFragment.mBinding.encryptionError.setVisibility(View.VISIBLE);
  }

  @Override
  public void onEncryptSeedSuccess() {
    mBinding.setImportStep(Constants.SEED_SPAWN_COMPLETE);
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    prefs.edit().putInt(Constants.SETTING_WALLET_ORIGIN, Constants.WALLET_ORIGIN_RESTORED_FROM_SEED).apply();
    new Handler().postDelayed(this::goToStartup, 1700);
  }

}
