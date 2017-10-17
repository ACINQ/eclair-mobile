package fr.acinq.eclair.wallet.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.tasks.QRCodeTask;

public class ReceivePaymentFragment extends Fragment implements QRCodeTask.AsyncQRCodeResponse {
  private static final String TAG = "ReceivePayment";
  private View mView;
  private ImageView mQRImageView;
  private TextView mAddressTextView;
  private String address;
  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(false);
  }

  @Override
  public void onResume() {
    super.onResume();
    try {
      address = ((App) getActivity().getApplication()).getWalletPublicAddress();
    } catch (Exception e) {
      e.printStackTrace();
    }
    mAddressTextView.setText(address);
    new QRCodeTask(this, address, 700, 700).execute();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    mView = inflater.inflate(R.layout.fragment_receive_payment, container, false);
    mQRImageView = (ImageView) mView.findViewById(R.id.receivepayment_qr);
    mAddressTextView = (TextView) mView.findViewById(R.id.receivepayment_address);
    return mView;
  }

  @Override
  public void processFinish(Bitmap output) {
    if (output != null) {
      mQRImageView.setImageBitmap(output);
    }
  }

  public void copyReceptionAddress() {
    ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText("Bitcoin address", address));
    Toast.makeText(this.getContext(), "Copied address to clipboard", Toast.LENGTH_SHORT).show();
  }
}
