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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;
import com.google.android.gms.common.util.Strings;
import fr.acinq.bitcoin.MnemonicCode;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.adapters.SimplePagerAdapter;
import fr.acinq.eclair.wallet.databinding.ActivityRestoreSeedBinding;
import fr.acinq.eclair.wallet.fragments.initwallet.WalletEncryptFragment;
import fr.acinq.eclair.wallet.fragments.initwallet.WalletImportSeedFragment;
import fr.acinq.eclair.wallet.fragments.initwallet.WalletPassphraseFragment;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.EncryptedSeed;
import fr.acinq.eclair.wallet.utils.WalletUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    if (mWalletImportSeedFragment != null && mWalletEncryptFragment.mBinding != null) {
      mWalletImportSeedFragment.mBinding.mnemonicsInput.clearAnimation();
    }
    seedErrorHandler.removeCallbacksAndMessages(null);

    try {
      final String mnemonics = mWalletImportSeedFragment.mBinding.mnemonicsInput.getText().toString().trim().toLowerCase();
      if (Strings.isEmptyOrWhitespace(mnemonics)) {
        handleSeedError(R.string.importwallet_seed_empty, null);
      } else {
        MnemonicCode.validate(mnemonics);
        goToPassphrase();
      }
    } catch (Exception e) {
      handleSeedError(R.string.importwallet_seed_error, e.getLocalizedMessage());
    }
  }

  private void handleSeedError(final int errorCode, @Nullable final String message) {
    if (mWalletImportSeedFragment != null && mWalletImportSeedFragment.mBinding != null) {
      mWalletImportSeedFragment.mBinding.mnemonicsInput.startAnimation(mErrorAnimation);
      if (message != null) {
        mWalletImportSeedFragment.mBinding.seedError.setText(getString(errorCode, message));
      } else {
        mWalletImportSeedFragment.mBinding.seedError.setText(errorCode);
      }
      mWalletImportSeedFragment.mBinding.seedError.setVisibility(View.VISIBLE);
      seedErrorHandler.postDelayed(() -> {
        mWalletImportSeedFragment.mBinding.seedError.setVisibility(View.GONE);
      }, 5000);
    } else {
      goToStartup();
    }
  }

  public void goToPassphraseConfirmStep(final View view) {
    try {
      final String mnemonics = mWalletImportSeedFragment.mBinding.mnemonicsInput.getText().toString().trim().toLowerCase();
      final String passphrase = mWalletPassphraseFragment.mBinding.passphraseInput.getText().toString();
      // check validity
      WalletUtils.mnemonicsToSeed(Arrays.asList(mnemonics.split(" ")), passphrase);
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
    if (mWalletEncryptFragment != null && mWalletEncryptFragment.mBinding != null) {
      mWalletEncryptFragment.mBinding.encryptionError.setVisibility(View.GONE);
    }
    mBinding.setImportStep(Constants.SEED_SPAWN_ENCRYPTION);
    new Thread() {
      @Override
      public void run() {
        try {
          final String mnemonics = mWalletImportSeedFragment.mBinding.mnemonicsInput.getText().toString().trim().toLowerCase();
          final String passphrase = mWalletPassphraseFragment.mBinding.passphraseInput.getText().toString();
          if (mnemonics.equals("")) {
            // reference was lost -- cannot continue
            throw new RuntimeException(getString(R.string.createwallet_invalid_seed));
          }
          final File datadir = new File(getFilesDir(), Constants.ECLAIR_DATADIR);
          final byte[] seed = WalletUtils.encodeMnemonics(EncryptedSeed.SEED_FILE_VERSION_2, Arrays.asList(mnemonics.split(" ")), passphrase);
          runOnUiThread(() -> encryptWallet(RestoreSeedActivity.this, false, datadir, seed, EncryptedSeed.SEED_FILE_VERSION_2));
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
    if (mWalletEncryptFragment != null && mWalletEncryptFragment.mBinding != null) {
      mBinding.setImportStep(2);
      goToEncryption();
      mWalletEncryptFragment.mBinding.encryptionError.setText(message);
      mWalletEncryptFragment.mBinding.encryptionError.setVisibility(View.VISIBLE);
    } else {
      goToStartup();
    }
  }

  @Override
  public void onEncryptSeedSuccess() {
    mBinding.setImportStep(Constants.SEED_SPAWN_COMPLETE);
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    prefs.edit().putInt(Constants.SETTING_WALLET_ORIGIN, Constants.WALLET_ORIGIN_RESTORED_FROM_SEED).apply();
    new Handler().postDelayed(this::goToStartup, 1700);
  }

}
