package fr.acinq.eclair.swordfish;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

/**
 * Created by Dominique on 26/05/2017.
 */

public class ManualChannelDialog extends DialogFragment {
  public interface ManualChannelDialogListener {
    public void onDialogPositiveClick(ManualChannelDialog dialog, String uri);
  }
  ManualChannelDialogListener mListener;

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    LayoutInflater inflater = getActivity().getLayoutInflater();
    final View dialogView = inflater.inflate(R.layout.dialog_manualchannel, null);
    builder.setView(dialogView)
      .setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int id) {
          EditText uri = (EditText) dialogView.findViewById(R.id.ocm__input_host);
          mListener.onDialogPositiveClick(ManualChannelDialog.this, uri.getText().toString());
        }
      })
      .setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int id) {}
      });
    return builder.create();
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    try {
      mListener = (ManualChannelDialogListener) context;
    } catch (ClassCastException e) {
      // The activity doesn't implement the interface, throw exception
      throw new ClassCastException(context.toString() + " must implement ChannelDialogListener");
    }
  }

}
