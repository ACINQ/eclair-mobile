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

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.acinq.eclair.blockchain.electrum.ElectrumWallet;
import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.tasks.QRCodeTask;

public class ReceivePaymentFragment extends Fragment implements QRCodeTask.AsyncQRCodeResponse {
  private final Logger log = LoggerFactory.getLogger(ReceivePaymentFragment.class);

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
}
