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
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.DialogPaymentRequestParametersBinding;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;

public class PaymentRequestParametersDialog extends Dialog {
  private final Logger log = LoggerFactory.getLogger(PaymentRequestParametersDialog.class);
  private DialogPaymentRequestParametersBinding mBinding;
  private CoinUnit prefUnit = WalletUtils.getPreferredCoinUnit(PreferenceManager.getDefaultSharedPreferences(this.getContext()));

  public PaymentRequestParametersDialog(final Context context, final @NonNull PaymentRequestParametersDialogCallback callback) {
    super(context, R.style.CustomDialog);
    mBinding = DataBindingUtil.inflate(LayoutInflater.from(getContext()), R.layout.dialog_payment_request_parameters, null, false);
    setContentView(mBinding.getRoot());

    setOnCancelListener(v -> dismiss());
    mBinding.cancel.setOnClickListener(v -> dismiss());
    mBinding.confirm.setOnClickListener(v -> {
      final String amountString = mBinding.amount.getText().toString();
      final MilliSatoshi amountMsat1 = amountString.length() == 0 ? null : new MilliSatoshi(CoinUtils.convertStringAmountToMsat(amountString, prefUnit.code()).amount());
      if (amountMsat1 != null && (amountMsat1.amount() <= 0 || amountMsat1.amount() > PaymentRequest.MAX_AMOUNT().amount())) {
        mBinding.amountError.setText(context.getString(R.string.dialog_prparams_amount_error, "0", CoinUtils.formatAmountInUnit(PaymentRequest.MAX_AMOUNT(), prefUnit, true)));
        mBinding.amountError.setVisibility(View.VISIBLE);
      } else {
        mBinding.amountError.setVisibility(View.GONE);
        callback.onConfirm(PaymentRequestParametersDialog.this, mBinding.description.getText().toString(), Option.apply(amountMsat1));
      }
    });
    mBinding.amountSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        log.info("amount seekbar =>" + progress);
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {

      }

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {

      }
    });
    mBinding.amountLayout.setHint(context.getString(R.string.dialog_prparams_amount, prefUnit.shortLabel()));
  }

  public void setParams(final String description, final Option<MilliSatoshi> amountMsat) {
    mBinding.description.setText(description);
    if (amountMsat.isDefined()) {
      mBinding.amount.setText(CoinUtils.rawAmountInUnit(amountMsat.get(), prefUnit).bigDecimal().toPlainString());
    }
  }

  public interface PaymentRequestParametersDialogCallback {
    void onConfirm(final PaymentRequestParametersDialog dialog, final String description, final Option<MilliSatoshi> amount);
  }
}
