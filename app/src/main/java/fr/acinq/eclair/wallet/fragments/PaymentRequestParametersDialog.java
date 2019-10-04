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
import android.preference.PreferenceManager;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;

import com.google.common.base.Strings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.acinq.eclair.MilliSatoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.actors.NodeSupervisor;
import fr.acinq.eclair.wallet.databinding.DialogPaymentRequestParametersBinding;
import fr.acinq.eclair.wallet.utils.TechnicalHelper;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import scala.Option;

public class PaymentRequestParametersDialog extends Dialog {
  private final Logger log = LoggerFactory.getLogger(PaymentRequestParametersDialog.class);
  private DialogPaymentRequestParametersBinding mBinding;
  private CoinUnit prefUnit;
  private String fiatUnit;
  private final MilliSatoshi maxReceivableAmount = NodeSupervisor.getMaxReceivable();

  public PaymentRequestParametersDialog(final Context context, final @NonNull PaymentRequestParametersDialogCallback callback) {
    super(context, R.style.CustomDialog);
    mBinding = DataBindingUtil.inflate(LayoutInflater.from(getContext()), R.layout.dialog_payment_request_parameters, null, false);
    setContentView(mBinding.getRoot());

    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getContext());
    prefUnit = WalletUtils.getPreferredCoinUnit(prefs);
    fiatUnit = WalletUtils.getPreferredFiat(prefs);

    setOnCancelListener(v -> dismiss());
    mBinding.amountTitle.setText(context.getString(R.string.dialog_prparams_amount_title));
    mBinding.amountUnit.setText(prefUnit.shortLabel());
    mBinding.amount.addTextChangedListener(new TechnicalHelper.SimpleTextWatcher() {
      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        extractAmount(s.toString());
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
  private MilliSatoshi extractAmount(final String amountString) {
    try {
      mBinding.setAmountWarning(null);
      mBinding.setAmountError(null);

      if (Strings.isNullOrEmpty(amountString)) {
        mBinding.amountFiat.setText("");
      } else {
        final MilliSatoshi amount = CoinUtils.convertStringAmountToMsat(amountString, prefUnit.code());
        mBinding.amountFiat.setText(getContext().getString(R.string.amount_to_fiat, WalletUtils.formatMsatToFiatWithUnit(amount, fiatUnit)));
        if (amount.$greater(maxReceivableAmount)) {
          mBinding.setAmountWarning(getContext().getString(R.string.dialog_prparams_amount_error_excessive, CoinUtils.formatAmountInUnit(maxReceivableAmount, prefUnit, true)));
        } else {
          mBinding.setAmountError(null);
          mBinding.setAmountWarning(null);
        }
        return amount;
      }
    } catch (Throwable t) {
      log.info("could not read requested payment amount: ", t);
      mBinding.setAmountError(getContext().getString(R.string.dialog_prparams_amount_error_generic));
      mBinding.amountFiat.setText("");
    }
    return null;
  }

  void setParams(final String description, final Option<MilliSatoshi> amount) {
    mBinding.description.setText(description);
    if (amount.isDefined()) {
      mBinding.amount.setText(CoinUtils.rawAmountInUnit(amount.get(), prefUnit).bigDecimal().toPlainString());
    } else {
      mBinding.amount.setText("");
    }
  }

  public interface PaymentRequestParametersDialogCallback {
    void onConfirm(final PaymentRequestParametersDialog dialog, final String description, final Option<MilliSatoshi> amount);
  }
}
