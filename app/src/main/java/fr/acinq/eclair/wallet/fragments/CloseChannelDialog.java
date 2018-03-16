package fr.acinq.eclair.wallet.fragments;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;
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
  private TextView mForceWarningText;
  private Button mCancelButton;
  private Button mMutualCloseButton;
  private Button mForceCloseButton;
  private CloseChannelDialogCallback mCallback;

  public CloseChannelDialog(final Context context, final @NotNull CloseChannelDialogCallback callback, final int themeResId, final ActorRef actor, final String channelState, final boolean mutualAllowed, final boolean forceAllowed) {
    super(context, themeResId);

    mCallback = callback;

    setContentView(R.layout.dialog_close_channel);
    mInfoText = findViewById(R.id.close_channel_info);
    mForceWarningText = findViewById(R.id.close_channel_force_warning);
    mCancelButton = findViewById(R.id.close_channel_cancel);
    mMutualCloseButton = findViewById(R.id.close_channel_mutual_close);
    mForceCloseButton = findViewById(R.id.close_channel_force_close);

    final String optionsMessage = mutualAllowed && forceAllowed ? "mutual and force" : mutualAllowed ? "mutual only" : "force only";
    mInfoText.setText(context.getString(R.string.dialog_close_channel_info, optionsMessage));

    mMutualCloseButton.setVisibility(mutualAllowed ? View.VISIBLE : View.GONE);
    mForceWarningText.setVisibility(forceAllowed ? View.VISIBLE : View.GONE);
    mForceCloseButton.setVisibility(forceAllowed ? View.VISIBLE : View.GONE);

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
    mForceCloseButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        actor.tell(CMD_FORCECLOSE$.MODULE$, actor);
        dismiss();
        mCallback.onCloseConfirm(CloseChannelDialog.this);
      }
    });
    mMutualCloseButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        actor.tell(CMD_CLOSE.apply(none), actor);
        dismiss();
        mCallback.onCloseConfirm(CloseChannelDialog.this);
      }
    });
  }

  public interface CloseChannelDialogCallback {
    void onCloseConfirm(final CloseChannelDialog dialog);
  }
}
