package fr.acinq.eclair.wallet.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import org.greenrobot.greendao.annotation.NotNull;

import java.io.File;
import java.security.MessageDigest;

import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.fragments.PinDialog;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class EclairActivity extends AppCompatActivity {

  protected App app;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    app = ((App) getApplication());
  }

  protected boolean checkInit() {
    if (app == null || app.appKit == null || app.getDBHelper() == null) {
      Intent startup = new Intent(this, StartupActivity.class);
      startup.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      startup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(startup);
      return false;
    }
    return true;
  }

  protected boolean isPinRequired () {
    return getApplicationContext().getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, MODE_PRIVATE)
      .getBoolean(Constants.SETTING_ASK_PIN_FOR_SENSITIVE_ACTIONS, false);
  }

  @SuppressLint("ApplySharedPref")
  protected boolean isPinCorrect (final String pin, @NotNull final PinDialog dialog) {
    final boolean isCorrect = MessageDigest.isEqual(pin.getBytes(), app.pin.get().getBytes());
    if (isCorrect) {
      dialog.animateSuccess();
    } else {
      dialog.animateFailure();
    }
    return isCorrect;
  }

  protected void encryptWallet(final EncryptSeedCallback callback, final boolean cancelable, final File datadir, final byte[] seed) {
    final PinDialog firstPinDialog = new PinDialog(EclairActivity.this, R.style.CustomAlertDialog, new PinDialog.PinDialogCallback() {
      @Override
      public void onPinConfirm(final PinDialog pFirstDialog, final String newPinValue) {
        final PinDialog confirmationDialog = new PinDialog(EclairActivity.this, R.style.CustomAlertDialog, new PinDialog.PinDialogCallback() {
          @Override
          public void onPinConfirm(final PinDialog pConfirmDialog, final String confirmPinValue) {
            if (newPinValue == null || newPinValue.length() != Constants.PIN_LENGTH) {
              callback.onEncryptSeedFailure(getString(R.string.pindialog_error));
            } else if (!newPinValue.equals(confirmPinValue)) {
              callback.onEncryptSeedFailure(getString(R.string.pindialog_error_donotmatch));
            } else {
              try {
                WalletUtils.writeSeedFile(datadir, seed, confirmPinValue);
                app.pin.set(confirmPinValue);
                callback.onEncryptSeedSuccess();
              } catch (Throwable t) {
                callback.onEncryptSeedFailure(getString(R.string.seed_encrypt_general_failure));
              }
            }
            pConfirmDialog.dismiss();
          }
          @Override
          public void onPinCancel(final PinDialog dialog) {
          }
        }, getString(R.string.seed_encrypt_prompt_confirm));
        confirmationDialog.setCanceledOnTouchOutside(cancelable);
        confirmationDialog.setCancelable(cancelable);
        pFirstDialog.dismiss();
        confirmationDialog.show();
      }

      @Override
      public void onPinCancel(final PinDialog dialog) {
      }
    }, getString(R.string.seed_encrypt_prompt));
    firstPinDialog.setCanceledOnTouchOutside(cancelable);
    firstPinDialog.setCancelable(cancelable);
    firstPinDialog.show();
  }

  public interface EncryptSeedCallback {
    void onEncryptSeedFailure(final String message);
    void onEncryptSeedSuccess();
  }

}

