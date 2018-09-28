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

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.view.View;
import android.widget.TextView;

import java.io.File;
import java.security.MessageDigest;

import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.fragments.PinDialog;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public abstract class EclairActivity extends AppCompatActivity {

  protected App app;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    app = ((App) getApplication());
  }

  protected void restart() {
    Intent intent = new Intent(getApplicationContext(), StartupActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
    PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT);

    if (app != null) app.system.shutdown();
    AlarmManager manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    if (manager != null) {
      manager.set(AlarmManager.RTC, System.currentTimeMillis() + 500, pendingIntent);
    }
    finishAndRemoveTask();
    finishAffinity();
    Runtime.getRuntime().exit(0);
  }

  protected AlertDialog.Builder getCustomDialog(final int contentId) {
    return getCustomDialog(getString(contentId));
  }

  protected AlertDialog.Builder getCustomDialog(final String message) {
    final AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomAlertDialog);
    final View v = getLayoutInflater().inflate(R.layout.custom_alert, null);
    final TextView content = v.findViewById(R.id.alert_content);
    content.setText(Html.fromHtml(message));
    builder.setView(v);
    return builder;
  }

  /**
   * Checks that the application was correctly initialized before accessing this activity. Redirect to Startup if not, which
   * restarts eclair correctly.
   */
  protected boolean checkInit() {
    if (app == null || app.appKit == null || app.getDBHelper() == null || app.pin.get() == null) {
      final Intent startup = new Intent(this, StartupActivity.class);
      startup.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
      startup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(startup);
      return false;
    }
    return true;
  }

  /**
   * Adds an origin when checking that the application was correctly initialized before accessing this activity.
   * Enables redirection to the original activity that was intended once the app is started.
   *
   * @param origin class name of the origin activity
   * @param extra extra parameter for this activity (such as an id)
   * @return
   */
  protected boolean checkInit(final String origin, final String extra) {
    if (app == null || app.appKit == null || app.getDBHelper() == null || app.pin.get() == null) {
      final Intent startup = new Intent(this, StartupActivity.class);
      startup.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
      startup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startup.putExtra(StartupActivity.ORIGIN, origin);
      startup.putExtra(StartupActivity.ORIGIN_EXTRA, extra);
      startActivity(startup);
      return false;
    }
    return true;
  }

  protected boolean isPinRequired () {
    return EclairActivity.this.getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, MODE_PRIVATE)
      .getBoolean(Constants.SETTING_ASK_PIN_FOR_SENSITIVE_ACTIONS, false);
  }

  @SuppressLint("ApplySharedPref")
  protected boolean isPinCorrect (final String pin, final PinDialog dialog) {
    if (checkInit()) {
      final boolean isCorrect = MessageDigest.isEqual(pin.getBytes(), app.pin.get().getBytes());
      if (isCorrect) {
        dialog.animateSuccess();
      } else {
        dialog.animateFailure();
      }
      return isCorrect;
    } else {
      return false;
    }
  }

  protected void encryptWallet(final EncryptSeedCallback callback, final boolean cancelable, final File datadir, final byte[] seed) {
    final PinDialog firstPinDialog = new PinDialog(EclairActivity.this, R.style.FullScreenDialog, new PinDialog.PinDialogCallback() {
      @Override
      public void onPinConfirm(final PinDialog pFirstDialog, final String newPinValue) {
        final PinDialog confirmationDialog = new PinDialog(EclairActivity.this, R.style.FullScreenDialog, new PinDialog.PinDialogCallback() {
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
        if (!EclairActivity.this.isFinishing()) confirmationDialog.show();
      }

      @Override
      public void onPinCancel(final PinDialog dialog) {
      }
    }, getString(R.string.seed_encrypt_prompt));
    firstPinDialog.setCanceledOnTouchOutside(cancelable);
    firstPinDialog.setCancelable(cancelable);
    if (!EclairActivity.this.isFinishing()) firstPinDialog.show();
  }

  public interface EncryptSeedCallback {
    void onEncryptSeedFailure(final String message);
    void onEncryptSeedSuccess();
  }

}

