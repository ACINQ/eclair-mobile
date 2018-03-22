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

  public void importMnemonics(View view) {
    reset();
    final String phrase = mBinding.mnemonicsInput.getText().toString().trim();
    if (Strings.isNullOrEmpty(phrase)) {
      showError(getString(R.string.importwallet_error, "can not be empty"));
    } else {
      final List<String> mnemonics = Arrays.asList(phrase.split(" "));
      if (mnemonics.size() != 12 && mnemonics.size() != 24) {
        showError(getString(R.string.importwallet_error, "must count 12 or 24 words separated by spaces"));
      } else {
        final File datadir = new File(getFilesDir(), Constants.ECLAIR_DATADIR);
        final byte[] seed = MnemonicCode.toSeed(JavaConverters.collectionAsScalaIterableConverter(mnemonics).asScala().toSeq(), "").toString().getBytes();
        encryptWallet(this, false, datadir, seed);
      }
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
