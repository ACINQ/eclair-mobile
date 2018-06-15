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
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.design.widget.TextInputLayout;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.greenrobot.greendao.annotation.NotNull;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import scala.Option;

public class PaymentRequestParametersDialog extends Dialog {

  private static final String TAG = PaymentRequestParametersDialog.class.getSimpleName();

  private PaymentRequestParametersDialogCallback mCallback;

  public PaymentRequestParametersDialog(final Context context, final @NotNull PaymentRequestParametersDialogCallback callback, final int themeResId) {
    super(context, themeResId);
    mCallback = callback;
    setContentView(R.layout.dialog_payment_request_parameters);
    final EditText descriptionEdit = findViewById(R.id.description);
    final EditText amountEdit = findViewById(R.id.amount);
    final TextInputLayout amountLayout = findViewById(R.id.amount_layout);
    final TextView amountError = findViewById(R.id.amount_error);
    final Button mCancelButton = findViewById(R.id.close_channel_cancel);
    final Button mCloseButton = findViewById(R.id.close_channel_close);
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getContext());
    final CoinUnit prefUnit = WalletUtils.getPreferredCoinUnit(prefs);
    amountLayout.setHint(context.getString(R.string.dialog_prparams_amount, prefUnit.shortLabel()));

    setOnCancelListener(new OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialogInterface) {
        dismiss();
      }
    });
    mCancelButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        dismiss();
      }
    });
    mCloseButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        final MilliSatoshi amountMsat = amountEdit.getText().length() == 0 ? null : new MilliSatoshi(
          CoinUtils.convertStringAmountToMsat(amountEdit.getText().toString(), prefUnit.code()).amount());
        if (amountMsat != null && (amountMsat.amount() <= 0 || amountMsat.amount() > PaymentRequest.MAX_AMOUNT().amount())) {
          amountError.setText(context.getString(R.string.dialog_prparams_amount_error, "0", CoinUtils.formatAmountInUnit(PaymentRequest.MAX_AMOUNT(), prefUnit, true)));
          amountError.setVisibility(View.VISIBLE);
        } else {
          amountError.setVisibility(View.GONE);
          mCallback.onConfirm(PaymentRequestParametersDialog.this, descriptionEdit.getText().toString(), Option.apply(amountMsat));
        }
      }
    });
  }

  public interface PaymentRequestParametersDialogCallback {
    void onConfirm(final PaymentRequestParametersDialog dialog, final String description, final Option<MilliSatoshi> amount);
  }
}
