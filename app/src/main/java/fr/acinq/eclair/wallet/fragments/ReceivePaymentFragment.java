package fr.acinq.eclair.wallet.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import fr.acinq.eclair.blockchain.electrum.ElectrumWallet;
import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.events.ChannelUpdateEvent;
import fr.acinq.eclair.wallet.events.WalletBalanceUpdateEvent;
import fr.acinq.eclair.wallet.tasks.QRCodeTask;

public class ReceivePaymentFragment extends Fragment implements QRCodeTask.AsyncQRCodeResponse {
  private static final String TAG = "ReceivePayment";
  private View mView;
  private ImageView mQRImageView;
  private TextView mAddressTextView;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(false);
  }

  @Override
  public void onResume() {
    super.onResume();
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this);
    }
    displayAddress();
  }

  @Override
  public void onPause() {
    EventBus.getDefault().unregister(this);
    super.onPause();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleNewWalletAddreess(final ElectrumWallet.NewWalletReceiveAddress addressEvent) {
    displayAddress();
  }

  private void displayAddress() {

        mAddressTextView.setText(getAddress());
        new QRCodeTask(this, getAddress(), 700, 700).execute();
  }

  private String getAddress() {
    if (getActivity() != null && getActivity().getApplication() != null) {
      return ((App) getActivity().getApplication()).getWalletAddress();
    } else return "Not available";
  }

  @Override
  public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                           Bundle savedInstanceState) {
    mView = inflater.inflate(R.layout.fragment_receive_payment, container, false);
    mQRImageView = mView.findViewById(R.id.receivepayment_qr);
    mAddressTextView = mView.findViewById(R.id.receivepayment_address);
    return mView;
  }

  @Override
  public void processFinish(final Bitmap output) {
    if (output != null) {
      mQRImageView.setImageBitmap(output);
    }
  }

  public void copyReceptionAddress() {
    ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText("Bitcoin address", getAddress()));
    Toast.makeText(this.getContext(), "Copied address to clipboard", Toast.LENGTH_SHORT).show();
  }
}
