package fr.acinq.eclair.wallet.fragments;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Html;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import org.greenrobot.greendao.annotation.NotNull;

import akka.actor.ActorRef;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.eclair.channel.CMD_CLOSE;
import fr.acinq.eclair.channel.CMD_FORCECLOSE$;
import fr.acinq.eclair.wallet.R;

public class CloseChannelDialog extends Dialog {

  private static final String TAG = "CloseChannelDialog";

  private TextView mInfoText;
  private CheckBox mForceCheckbox;
  private TextView mForceWarningText;
  private Button mCancelButton;
  private Button mCloseButton;
  private CloseChannelDialogCallback mCallback;

  public CloseChannelDialog(final Context context, final @NotNull CloseChannelDialogCallback callback, final int themeResId, final ActorRef actor, final boolean mutualAllowed, final boolean forceAllowed) {
    super(context, themeResId);

    mCallback = callback;

    setContentView(R.layout.dialog_close_channel);
    mInfoText = findViewById(R.id.close_channel_info);
    mForceWarningText = findViewById(R.id.close_channel_force_warning);
    mForceCheckbox = findViewById(R.id.close_channel_force_checkbox);
    mCancelButton = findViewById(R.id.close_channel_cancel);
    mCloseButton = findViewById(R.id.close_channel_close);

    final String optionsMessage = mutualAllowed && forceAllowed ? "mutual and force" : mutualAllowed ? "mutual only" : "force only";
    mInfoText.setText(Html.fromHtml(context.getString(R.string.dialog_close_channel_info, optionsMessage)));

    // if only force close is allowed, checkbox is true and hidden and warning is always shown
    if (!mutualAllowed && forceAllowed) {
      mForceCheckbox.setChecked(true);
      mForceWarningText.setVisibility(View.VISIBLE);
    } else {
      mForceCheckbox.setVisibility(forceAllowed ? View.VISIBLE : View.GONE);
      mForceCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
          mForceWarningText.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        }
      });
    }

    final scala.Option<BinaryData> none = scala.Option.apply(null);
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
        if (mForceCheckbox.isChecked()) {
          actor.tell(CMD_FORCECLOSE$.MODULE$, actor);
        } else {
          actor.tell(CMD_CLOSE.apply(none), actor);
        }
        dismiss();
        mCallback.onCloseConfirm(CloseChannelDialog.this);
      }
    });
  }

  public interface CloseChannelDialogCallback {
    void onCloseConfirm(final CloseChannelDialog dialog);
  }
}
