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
import com.google.common.base.Strings;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.actors.NodeSupervisor;
import fr.acinq.eclair.wallet.databinding.DialogPaymentRequestParametersBinding;
import fr.acinq.eclair.wallet.utils.TechnicalHelper;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;

public class PaymentRequestParametersDialog extends Dialog {
  private final Logger log = LoggerFactory.getLogger(PaymentRequestParametersDialog.class);
  private DialogPaymentRequestParametersBinding mBinding;
  private CoinUnit prefUnit = WalletUtils.getPreferredCoinUnit(PreferenceManager.getDefaultSharedPreferences(this.getContext()));
  private final MilliSatoshi maxReceivableAmount = NodeSupervisor.getMaxReceivable();

  public PaymentRequestParametersDialog(final Context context, final @NonNull PaymentRequestParametersDialogCallback callback) {
    super(context, R.style.CustomDialog);
    mBinding = DataBindingUtil.inflate(LayoutInflater.from(getContext()), R.layout.dialog_payment_request_parameters, null, false);
    setContentView(mBinding.getRoot());

    setOnCancelListener(v -> dismiss());
    mBinding.amountTitle.setText(context.getString(R.string.dialog_prparams_amount_title, CoinUtils.formatAmountInUnit(maxReceivableAmount, prefUnit, true)));
    mBinding.amountUnit.setText(prefUnit.shortLabel());
    mBinding.amount.addTextChangedListener(new TechnicalHelper.SimpleTextWatcher() {
      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (s == null || Strings.isNullOrEmpty(s.toString())) {
          mBinding.setAmountWarning(null);
          mBinding.setAmountError(null);
        } else {
          extractAmount(s.toString());
        }
      }
    });
    mBinding.cancel.setOnClickListener(v -> dismiss());
    mBinding.confirm.setOnClickListener(v -> {
      final String amountString = mBinding.amount.getText().toString();
      if (Strings.isNullOrEmpty(amountString)) {
        callback.onConfirm(PaymentRequestParametersDialog.this, mBinding.description.getText().toString(), Option.apply(null));
      } else {
        final MilliSatoshi amount = extractAmount(amountString);
        if (amount != null) {
          callback.onConfirm(PaymentRequestParametersDialog.this, mBinding.description.getText().toString(), Option.apply(amount));
        }
      }
    });
  }

  /**
   * Extracts amount from input and converts it to MilliSatoshi. If amount is invalid, updates error binding to show
   * an error message and returns null.
   */
  private MilliSatoshi extractAmount(@NonNull final String amountString) {
    try {
      mBinding.setAmountWarning(null);
      mBinding.setAmountError(null);
      final MilliSatoshi amountMsat = amountString.length() == 0 ? null : new MilliSatoshi(CoinUtils.convertStringAmountToMsat(amountString, prefUnit.code()).amount());
      if (amountMsat != null && amountMsat.amount() < 0) {
        mBinding.setAmountError(getContext().getString(R.string.dialog_prparams_amount_error_generic));
      } else if (amountMsat != null && amountMsat.amount() > PaymentRequest.MAX_AMOUNT().amount()) {
        mBinding.setAmountError(getContext().getString(R.string.dialog_prparams_amount_error_excessive_absolute, CoinUtils.formatAmountInUnit(PaymentRequest.MAX_AMOUNT(), prefUnit, true)));
      } else {
        if (amountMsat.amount() > maxReceivableAmount.amount()) {
          mBinding.setAmountWarning(getContext().getString(R.string.dialog_prparams_amount_error_excessive));
        } else {
          mBinding.setAmountError(null);
          mBinding.setAmountWarning(null);
        }
        return amountMsat;
      }
    } catch (Throwable t) {
      log.info("could not read payment amount with cause=" + t.getLocalizedMessage());
      mBinding.setAmountError(getContext().getString(R.string.dialog_prparams_amount_error_generic));
    }
    return null;
  }

  void setParams(final String description, final Option<MilliSatoshi> amountMsat) {
    mBinding.description.setText(description);
    if (amountMsat.isDefined()) {
      mBinding.amount.setText(CoinUtils.rawAmountInUnit(amountMsat.get(), prefUnit).bigDecimal().toPlainString());
    } else {
      mBinding.amount.setText("");
    }
  }

  public interface PaymentRequestParametersDialogCallback {
    void onConfirm(final PaymentRequestParametersDialog dialog, final String description, final Option<MilliSatoshi> amount);
  }
}
