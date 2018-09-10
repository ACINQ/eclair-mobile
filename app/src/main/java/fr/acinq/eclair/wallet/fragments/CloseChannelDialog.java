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
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import akka.actor.ActorRef;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.eclair.channel.CMD_CLOSE;
import fr.acinq.eclair.channel.CMD_FORCECLOSE$;
import fr.acinq.eclair.wallet.R;

public class CloseChannelDialog extends Dialog {

  private static final String TAG = "CloseChannelDialog";

  private CheckBox mForceCheckbox;
  private TextView mForceWarningText;
  private Button mCancelButton;
  private Button mCloseButton;
  private CloseChannelDialogCallback mCallback;

  public CloseChannelDialog(final Context context, final CloseChannelDialogCallback callback, final ActorRef actor, final boolean mutualAllowed, final boolean forceAllowed) {
    super(context, R.style.CustomDialog);

    mCallback = callback;

    setContentView(R.layout.dialog_close_channel);
    mForceWarningText = findViewById(R.id.close_channel_force_warning);
    mForceCheckbox = findViewById(R.id.close_channel_force_checkbox);
    mCancelButton = findViewById(R.id.close_channel_cancel);
    mCloseButton = findViewById(R.id.close_channel_close);

    // if only force close is allowed, checkbox is true and hidden and warning is always shown
    if (!mutualAllowed && forceAllowed) {
      mForceCheckbox.setChecked(true);
      mForceCheckbox.setEnabled(false);
      mForceCheckbox.setVisibility(View.VISIBLE);
      mForceWarningText.setVisibility(View.VISIBLE);
      mCloseButton.setText(context.getString(R.string.dialog_close_channel_forceclose));
      mCloseButton.setTextColor(ContextCompat.getColor(context, R.color.red_faded));
    } else {
      mForceCheckbox.setVisibility(forceAllowed ? View.VISIBLE : View.GONE);
      mForceCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
        mForceWarningText.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        if (isChecked) {
          mCloseButton.setText(context.getString(R.string.dialog_close_channel_forceclose));
          mCloseButton.setTextColor(ContextCompat.getColor(context, R.color.red_faded));
        } else {
          mCloseButton.setText(context.getString(R.string.dialog_close_channel_close));
          mCloseButton.setTextColor(ContextCompat.getColor(context, R.color.grey_4));
        }
      });
    }

    final scala.Option<BinaryData> none = scala.Option.apply(null);
    setOnCancelListener(dialogInterface -> dismiss());
    mCancelButton.setOnClickListener(view -> dismiss());
    mCloseButton.setOnClickListener(view -> {
      if (mForceCheckbox.isChecked()) {
        actor.tell(CMD_FORCECLOSE$.MODULE$, actor);
      } else {
        actor.tell(CMD_CLOSE.apply(none), actor);
      }
      dismiss();
      mCallback.onCloseConfirm(CloseChannelDialog.this);
    });
  }

  public interface CloseChannelDialogCallback {
    void onCloseConfirm(final CloseChannelDialog dialog);
  }
}
