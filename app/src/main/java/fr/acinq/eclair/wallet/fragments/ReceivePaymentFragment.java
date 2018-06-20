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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Date;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.blockchain.electrum.ElectrumWallet;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.activities.EclairActivity;
import fr.acinq.eclair.wallet.databinding.FragmentReceivePaymentBinding;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDirection;
import fr.acinq.eclair.wallet.models.PaymentStatus;
import fr.acinq.eclair.wallet.models.PaymentType;
import fr.acinq.eclair.wallet.tasks.LightningPaymentRequestTask;
import fr.acinq.eclair.wallet.tasks.LightningQRCodeTask;
import fr.acinq.eclair.wallet.tasks.QRCodeTask;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import scala.Option;

public class ReceivePaymentFragment extends Fragment implements QRCodeTask.AsyncQRCodeResponse, LightningQRCodeTask.AsyncQRCodeResponse, LightningPaymentRequestTask.AsyncPaymentRequestResponse, PaymentRequestParametersDialog.PaymentRequestParametersDialogCallback {
  private static final String TAG = "ReceivePayment";
  private FragmentReceivePaymentBinding mBinding;
  private boolean isGeneratingPaymentRequest = false;

  private PaymentRequestParametersDialog mPRParamsDialog;

  private String lightningDescription = "";
  private Option<MilliSatoshi> lightningAmount = Option.apply(null);

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(false);
  }

  @Override
  public View onCreateView(final LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {
    mPRParamsDialog = new PaymentRequestParametersDialog(ReceivePaymentFragment.this.getContext(), ReceivePaymentFragment.this, R.style.CustomAlertDialog);
    mBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_receive_payment, container, false);
    mBinding.setPaymentType(0);
    mBinding.setLightningShowAdvanced(false);
    mBinding.pickOnchainButton.setOnClickListener(v -> mBinding.setPaymentType(0));
    mBinding.pickLightningButton.setOnClickListener(v -> {
      if (!isGeneratingPaymentRequest) setPaymentRequest();
      mBinding.setPaymentType(1);
    });
    mBinding.lightningParameters.setOnClickListener(v -> {
      if (!isGeneratingPaymentRequest) mPRParamsDialog.show();
    });
    return mBinding.getRoot();
  }

  @Override
  public void onResume() {
    super.onResume();
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this);
    }
    setOnchainAddress();
  }

  @Override
  public void onPause() {
    EventBus.getDefault().unregister(this);
    if (mPRParamsDialog != null) {
      mPRParamsDialog.dismiss();
    }
    super.onPause();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleNewWalletAddress(final ElectrumWallet.NewWalletReceiveAddress addressEvent) {
    setOnchainAddress();
  }

  private void setPaymentRequest() {
    loadingPaymentRequestFields();
    try {
      new LightningPaymentRequestTask(this, (EclairActivity) this.getActivity()).execute(this.lightningDescription, this.lightningAmount);
    } catch (Exception e) {
      failPaymentRequestFields();
      Log.e(TAG, "could not generate payment request", e);
    }
  }

  private void setOnchainAddress() {
    final String address = getApp() != null ? getApp().getWalletAddress() : getString(R.string.unknown);
    mBinding.onchainAddress.setText(address);
    mBinding.onchainAddress.setOnClickListener(v -> copyReceptionAddress(address));
    mBinding.onchainQr.setOnClickListener(v -> copyReceptionAddress(address));
    new QRCodeTask(this, address, 700, 700).execute();
  }

  @Override
  public void processFinish(final Bitmap output) {
    if (output != null) {
      mBinding.onchainQr.setImageBitmap(output);
    }
  }

  private App getApp() {
    return (getActivity() != null && getActivity().getApplication() != null) ? (App) getActivity().getApplication() : null;
  }

  private void copyReceptionAddress(final String address) {
    if (getActivity() == null) return;
    try {
      ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
      clipboard.setPrimaryClip(ClipData.newPlainText("Bitcoin payment request", address));
      Toast.makeText(getActivity().getApplicationContext(), "Copied to clipboard!", Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      Log.d(TAG, "failed to copy with cause=" + e.getMessage());
    }
  }

  @Override
  public void processLightningQRCodeFinish(Bitmap output) {
    if (output != null) {
      mBinding.lightningQr.setImageBitmap(output);
    }
  }

  @Override
  public void processLightningPaymentRequest(PaymentRequest paymentRequest) {
    if (paymentRequest == null) {
      failPaymentRequestFields();
    } else {
      final String paymentRequestStr = PaymentRequest.write(paymentRequest);
      final String description = paymentRequest.description().isLeft() ? paymentRequest.description().left().get() : paymentRequest.description().right().get().toString();
      Log.i(TAG, "successfully generated payment_request=" + paymentRequestStr);

      final Payment newPayment = new Payment();
      newPayment.setType(PaymentType.BTC_LN);
      newPayment.setDirection(PaymentDirection.RECEIVED);
      newPayment.setReference(paymentRequest.paymentHash().toString());
      newPayment.setAmountRequestedMsat(WalletUtils.getLongAmountFromInvoice(paymentRequest));
      newPayment.setRecipient(paymentRequest.nodeId().toString());
      newPayment.setPaymentRequest(paymentRequestStr.toLowerCase());
      newPayment.setStatus(PaymentStatus.INIT);
      newPayment.setDescription(description);
      newPayment.setUpdated(new Date());
      if (getApp() != null) getApp().getDBHelper().insertOrUpdatePayment(newPayment);

      this.lightningDescription = description;
      this.lightningAmount = paymentRequest.amount();
      updateLightningDescriptionView();
      updateLightningAmountView();

      mBinding.lightningPr.setText(paymentRequestStr);
      mBinding.lightningPr.setOnClickListener(v -> copyReceptionAddress(paymentRequestStr));
      mBinding.lightningQr.setOnClickListener(v -> copyReceptionAddress(paymentRequestStr));
      new LightningQRCodeTask(this, paymentRequestStr, 700, 700).execute();
    }
    isGeneratingPaymentRequest = false;
  }

  private void loadingPaymentRequestFields() {
    mBinding.lightningPr.setText(R.string.receivepayment_lightning_wait);
    mBinding.lightningQr.setImageDrawable(getResources().getDrawable(R.drawable.qrcode_placeholder));
  }

  private void failPaymentRequestFields() {
    mBinding.lightningPr.setText(R.string.receivepayment_lightning_error);
    this.lightningDescription = "";
    this.lightningAmount = Option.apply(null);
    updateLightningDescriptionView();
    updateLightningAmountView();
    mBinding.lightningQr.setImageDrawable(getResources().getDrawable(R.drawable.qrcode_placeholder));
  }

  @Override
  public void onConfirm(PaymentRequestParametersDialog dialog, String description, Option<MilliSatoshi> amount) {
    this.lightningDescription = description;
    this.lightningAmount = amount;
    dialog.dismiss();
    setPaymentRequest();
  }

  private void updateLightningDescriptionView() {
    if (this.lightningDescription == null || this.lightningDescription.length() == 0) {
      mBinding.lightningDescription.setText(getString(R.string.receivepayment_lightning_description_notset));
      if (getContext() != null) {
        mBinding.lightningDescription.setTextColor(ContextCompat.getColor(getContext(), R.color.grey_1));
        mBinding.lightningDescription.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
      }
    } else {
      mBinding.lightningDescription.setText(this.lightningDescription);
      if (getContext() != null) {
        mBinding.lightningDescription.setTextColor(ContextCompat.getColor(getContext(), R.color.grey_4));
        mBinding.lightningDescription.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
      }
    }
  }

  private void updateLightningAmountView() {
    if (this.lightningAmount == null || this.lightningAmount.isEmpty()) {
      mBinding.lightningAmount.setText(getString(R.string.receivepayment_lightning_amount_notset));
      if (getContext() != null) {
        mBinding.lightningAmount.setTextColor(ContextCompat.getColor(getContext(), R.color.grey_1));
        mBinding.lightningAmount.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
      }
    } else {
      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getContext());
      mBinding.lightningAmount.setText(Html.fromHtml(getString(R.string.receivepayment_lightning_amount_value,
        CoinUtils.formatAmountInUnit(this.lightningAmount.get(), WalletUtils.getPreferredCoinUnit(prefs), true),
        WalletUtils.convertMsatToFiatWithUnit(this.lightningAmount.get().amount(), WalletUtils.getPreferredFiat(prefs)))));
      if (getContext() != null) {
        mBinding.lightningAmount.setTextColor(ContextCompat.getColor(this.getContext(), R.color.grey_4));
        mBinding.lightningAmount.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
      }
    }
  }
}
