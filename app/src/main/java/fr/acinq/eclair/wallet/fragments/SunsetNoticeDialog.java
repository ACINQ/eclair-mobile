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
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;

import androidx.databinding.DataBindingUtil;

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.DialogCustomSunsetNoticeBinding;
import fr.acinq.eclair.wallet.utils.Constants;

public class SunsetNoticeDialog extends Dialog {

  public SunsetNoticeDialog(final Context context) {
    super(context, R.style.CustomDialog);
    final DialogCustomSunsetNoticeBinding binding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.dialog_custom_sunset_notice, null, false);
    setContentView(binding.getRoot());
    setOnCancelListener(d -> dismiss());
    binding.body.setMovementMethod(LinkMovementMethod.getInstance());
    binding.body.setText(Html.fromHtml(context.getString(R.string.dialog_sunset_body)));
    binding.btnOk.setOnClickListener(v -> {
      PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(Constants.SETTING_SHOW_SUNSET, false).apply();
      dismiss();
    });
  }
}
