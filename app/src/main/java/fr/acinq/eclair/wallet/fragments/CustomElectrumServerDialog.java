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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.google.common.base.Strings;
import com.google.common.net.HostAndPort;

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.DialogCustomElectrumServerBinding;
import fr.acinq.eclair.wallet.utils.Constants;

import static android.content.Context.INPUT_METHOD_SERVICE;

public class CustomElectrumServerDialog extends Dialog {

  public CustomElectrumServerDialog(final Context context, final ElectrumDialogCallback callback) {
    super(context, R.style.CustomDialog);
    final DialogCustomElectrumServerBinding binding = DataBindingUtil.inflate(LayoutInflater.from(
      getContext()), R.layout.dialog_custom_electrum_server, null, false);
    setContentView(binding.getRoot());
    setOnCancelListener(d -> dismiss());

    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
    final String serverString = prefs.getString(Constants.CUSTOM_ELECTRUM_SERVER, "").trim();
    binding.customCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
      if (isChecked) {
        binding.addressInputLayout.setAlpha(1f);
        binding.addressInput.setEnabled(true);
        binding.certifWarning.setVisibility(View.VISIBLE);
      } else {
        binding.addressInputLayout.setAlpha(0.3f);
        binding.addressInput.setEnabled(false);
        binding.certifWarning.setVisibility(View.GONE);
      }
    });
    binding.customCheckbox.setChecked(!Strings.isNullOrEmpty(serverString));
    binding.addressInput.setText(serverString);
    binding.addressInput.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        binding.addressInputLayout.setError(null);
        if (!validateAddress(s.toString().trim())) {
          binding.addressInputLayout.setError(context.getString(R.string.dialog_electrum_error));
        }
      }

      @Override
      public void afterTextChanged(Editable s) {
      }
    });
    binding.cancel.setOnClickListener(v -> dismiss());
    binding.submit.setOnClickListener(v -> {
      if (binding.addressInputLayout.getError() != null) return;
      final String serverAddress = binding.customCheckbox.isChecked() ? binding.addressInput.getText().toString().trim() : "";
      final InputMethodManager imm = (InputMethodManager) context.getSystemService(INPUT_METHOD_SERVICE);
      if (imm != null && v != null) {
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
      }
      prefs.edit().putString(Constants.CUSTOM_ELECTRUM_SERVER, serverAddress).apply();
      callback.onElectrumServerChange(serverAddress);
      dismiss();
    });
  }

  /**
   * Return true if the address is valid, false otherwise. A null or empty string is valid.
   */
  private boolean validateAddress(final String address) {
    try {
      if (Strings.isNullOrEmpty(address)) {
        return true;
      }
      HostAndPort.fromString(address);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public interface ElectrumDialogCallback {
    void onElectrumServerChange(final String serverAddress);
  }
}
