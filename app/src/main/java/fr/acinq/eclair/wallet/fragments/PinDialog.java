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

package fr.acinq.eclair.wallet.fragments;

import android.app.Dialog;
import android.content.Context;
import androidx.databinding.DataBindingUtil;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import com.google.common.base.Strings;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.DialogPinBinding;
import fr.acinq.eclair.wallet.utils.Constants;

import java.util.ArrayList;
import java.util.List;

public class PinDialog extends Dialog {

  private static final String PIN_PLACEHOLDER = "\u25CF";
  private DialogPinBinding mBinding;
  private String mPinValue = "";
  private PinDialogCallback mPinCallback;

  public PinDialog(final Context context, final int themeResId, final PinDialogCallback pinCallback) {
    this(context, themeResId, pinCallback, context.getString(R.string.pindialog_title_default));
  }

  public PinDialog(final Context context, final int themeResId, final PinDialogCallback pinCallback, final String title) {
    super(context, themeResId);

    mPinCallback = pinCallback;
    mBinding = DataBindingUtil.inflate(LayoutInflater.from(getContext()), R.layout.dialog_pin, null, false);
    setContentView(mBinding.getRoot());
    setOnCancelListener(dialogInterface -> mPinCallback.onPinCancel(PinDialog.this));
    mBinding.pinTitle.setText(title);

    final List<View> mButtonsList = new ArrayList<>();
    mButtonsList.add(mBinding.pinNum1);
    mButtonsList.add(mBinding.pinNum2);
    mButtonsList.add(mBinding.pinNum3);
    mButtonsList.add(mBinding.pinNum4);
    mButtonsList.add(mBinding.pinNum5);
    mButtonsList.add(mBinding.pinNum6);
    mButtonsList.add(mBinding.pinNum7);
    mButtonsList.add(mBinding.pinNum8);
    mButtonsList.add(mBinding.pinNum9);
    mButtonsList.add(mBinding.pinNum0);

    mBinding.pinDisplay.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (s != null && s.length() == Constants.PIN_LENGTH) {
          // automatically confirm pin when pin is 6 chars long
          new Handler().postDelayed(() -> mPinCallback.onPinConfirm(PinDialog.this, mPinValue), 300);
        }
      }
      @Override
      public void afterTextChanged(Editable s) {}
    });

    for (View v : mButtonsList) {
      v.setOnClickListener(view -> {
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Constants.SETTING_HAPTIC_FEEDBACK, true)) {
          view.setHapticFeedbackEnabled(true);
          view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
        if (mPinValue == null) mPinValue = "";
        if (mPinValue.equals("") || mPinValue.length() != Constants.PIN_LENGTH) {
          final String val = ((Button) view).getText().toString();
          mPinValue = mPinValue.concat(val);
          mBinding.pinDisplay.setText(Strings.repeat(PIN_PLACEHOLDER, mPinValue.length()));
        }
      });
    }

    mBinding.pinNumClear.setOnClickListener(view -> {
      mPinValue = "";
      mBinding.pinDisplay.setText("");
    });

    mBinding.pinBackspace.setOnClickListener(view -> {
      if (mPinValue != null && mPinValue.length() > 0) {
        mPinValue = mPinValue.substring(0, mPinValue.length() - 1);
        mBinding.pinDisplay.setText(Strings.repeat(PIN_PLACEHOLDER, mPinValue.length()));
      }
    });
  }

  public void animateSuccess() {
    this.dismiss();
  }

  public void animateFailure() {
    this.dismiss();
  }

  public interface PinDialogCallback {
    void onPinConfirm(final PinDialog dialog, final String pinValue);

    void onPinCancel(final PinDialog dialog);
  }
}
