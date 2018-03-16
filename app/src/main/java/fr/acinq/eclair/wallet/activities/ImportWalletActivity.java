package fr.acinq.eclair.wallet.activities;

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.google.common.base.Strings;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityImportWalletBinding;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class ImportWalletActivity extends AppCompatActivity {

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
    startActivity(new Intent(getBaseContext(), StartupActivity.class));
  }

  public void importMnemonics(View view) {
    reset();
    final String phrase = mBinding.mnemonicsInput.getText().toString().trim();
    if (Strings.isNullOrEmpty(phrase)) {
      showError(getString(R.string.importwallet_error, "can not be empty"));
    } else {
      List<String> mnemonics = Arrays.asList(phrase.split(" "));
      if (mnemonics.size() != 12 && mnemonics.size() != 24) {
        showError(getString(R.string.importwallet_error, "must count 12 or 24 words separated by spaces"));
      } else {
        final File datadir = new File(getFilesDir(), Constants.ECLAIR_DATADIR);
        try {
          WalletUtils.writeSeedFile(datadir, mnemonics);
          startActivity(new Intent(getBaseContext(), StartupActivity.class));
        } catch (Exception e) {
          showError("Could not write seed to disk");
        }
      }
    }
  }
}
