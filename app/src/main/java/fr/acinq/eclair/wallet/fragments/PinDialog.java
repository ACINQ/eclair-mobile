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

package fr.acinq.eclair.wallet.fragments;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Strings;

import org.greenrobot.greendao.annotation.NotNull;

import java.util.ArrayList;
import java.util.List;

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.utils.Constants;

public class PinDialog extends Dialog {

  private static final String TAG = "PinDialog";
  private static final String PIN_PLACEHOLDER = "\u25CF";
  private TextView mPinTitle;
  private String mPinValue = "";
  private TextView mPinDisplay;
  private ImageButton mSubmitButton;
  private List<View> mButtonsList = new ArrayList<>();
  private PinDialogCallback mPinCallback;

  public PinDialog(final Context context, final int themeResId, final @NotNull PinDialogCallback pinCallback) {
    this(context, themeResId, pinCallback, context.getString(R.string.pindialog_title_default));
  }

  public PinDialog(final Context context, final int themeResId, final @NotNull PinDialogCallback pinCallback, final String title) {
    super(context, themeResId);

    // callback must be defined
    mPinCallback = pinCallback;

    // layout
    setContentView(R.layout.dialog_pin);

    setOnCancelListener(new OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialogInterface) {
        mPinCallback.onPinCancel(PinDialog.this);
      }
    });

    // set up pin numpad
    mPinTitle = findViewById(R.id.pin_title);
    mPinTitle.setText(title);
    mPinDisplay = findViewById(R.id.pin_display);
    mSubmitButton = findViewById(R.id.pin_submit);

    mButtonsList.add(findViewById(R.id.pin_num_1));
    mButtonsList.add(findViewById(R.id.pin_num_2));
    mButtonsList.add(findViewById(R.id.pin_num_3));
    mButtonsList.add(findViewById(R.id.pin_num_4));
    mButtonsList.add(findViewById(R.id.pin_num_5));
    mButtonsList.add(findViewById(R.id.pin_num_6));
    mButtonsList.add(findViewById(R.id.pin_num_7));
    mButtonsList.add(findViewById(R.id.pin_num_8));
    mButtonsList.add(findViewById(R.id.pin_num_9));
    mButtonsList.add(findViewById(R.id.pin_num_0));

    mPinDisplay.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (s == null || s.length() != Constants.PIN_LENGTH) {
          mSubmitButton.setAlpha(0.4f);
        } else {
          mSubmitButton.setAlpha(1f);
        }
      }
      @Override
      public void afterTextChanged(Editable s) {}
    });
    for (View v : mButtonsList) {
      v.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          if (mPinValue == null) mPinValue = "";
          if (mPinValue.equals("") || mPinValue.length() != Constants.PIN_LENGTH) {
            final String val = ((Button) view).getText().toString();
            mPinValue = mPinValue.concat(val);
            mPinDisplay.setText(Strings.repeat(PIN_PLACEHOLDER, mPinValue.length()));
          }
        }
      });
    }
    findViewById(R.id.pin_num_clear).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        mPinValue = "";
        mPinDisplay.setText("");
      }
    });
    mSubmitButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (mPinValue == null || mPinValue.length() != Constants.PIN_LENGTH) {
          Toast.makeText(view.getContext(), view.getResources().getString(R.string.pindialog_error), Toast.LENGTH_SHORT).show();
        } else {
          mPinCallback.onPinConfirm(PinDialog.this, mPinValue);
        }
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
