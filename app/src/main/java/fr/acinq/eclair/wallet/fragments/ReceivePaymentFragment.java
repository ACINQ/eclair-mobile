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

import android.content.*;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import com.google.common.base.Strings;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.blockchain.electrum.ElectrumWallet;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.actors.NodeSupervisor;
import fr.acinq.eclair.wallet.databinding.FragmentReceivePaymentBinding;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDirection;
import fr.acinq.eclair.wallet.models.PaymentStatus;
import fr.acinq.eclair.wallet.models.PaymentType;
import fr.acinq.eclair.wallet.tasks.LightningQRCodeTask;
import fr.acinq.eclair.wallet.tasks.QRCodeTask;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;

import java.util.Date;
import java.util.Objects;

public class ReceivePaymentFragment extends Fragment implements QRCodeTask.AsyncQRCodeResponse, LightningQRCodeTask.AsyncQRCodeResponse, PaymentRequestParametersDialog.PaymentRequestParametersDialogCallback {
  private final Logger log = LoggerFactory.getLogger(ReceivePaymentFragment.class);
  private FragmentReceivePaymentBinding mBinding;
  private PaymentRequestParametersDialog mPRParamsDialog;

  private boolean lightningUseDefaultDescription = true;
  private String lightningPaymentRequest = null;
  private String lightningPaymentHash = null;
  private String lightningDescription = "";
  private Option<MilliSatoshi> lightningAmount = Option.apply(null);

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(false);
  }

  @Override
  public View onCreateView(final LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {
    if (mBinding == null) {
      mBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_receive_payment, container, false);
      mBinding.setPaymentType(0);
      mBinding.pickOnchainButton.setOnClickListener(v -> mBinding.setPaymentType(0));
      mBinding.pickLightningButton.setOnClickListener(v -> {
        mBinding.setPaymentType(1);
        refreshLightningPaneState();
      });
      mBinding.lightningEditPr.setOnClickListener(v -> {
        if (!mBinding.getIsGeneratingLightningPR()) {
          if (mPRParamsDialog == null) {
            mPRParamsDialog = new PaymentRequestParametersDialog(ReceivePaymentFragment.this.getContext(), ReceivePaymentFragment.this);
          }
          mPRParamsDialog.setParams(this.lightningDescription, this.lightningAmount);
          mPRParamsDialog.show();
        }
      });
      mBinding.onchainShare.setOnClickListener(v -> {
        final String address = mBinding.getOnchainAddress();
        if (!Strings.isNullOrEmpty(address)) {
          final Intent shareIntent = new Intent(android.content.Intent.ACTION_SEND);
          shareIntent.setType("text/plain");
          shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.receivepayment_onchain_share_subject));
          shareIntent.putExtra(Intent.EXTRA_TEXT, "bitcoin:" + address);
          startActivity(Intent.createChooser(shareIntent, getString(R.string.receivepayment_onchain_share)));
        }
      });
      mBinding.lightningMaxWhat.setOnClickListener(v -> {
        if (getActivity() != null) {
          new AlertDialog.Builder(getActivity(), R.style.CustomDialog)
            .setTitle(R.string.receivepayment_lightning_max_what_title)
            .setMessage(R.string.receivepayment_lightning_max_what_body)
            .setPositiveButton(R.string.btn_ok, null)
            .show();
        }
      });
      mBinding.onchainAddressValue.setOnClickListener(v -> copyReceptionAddress(mBinding.getOnchainAddress()));
      mBinding.onchainQr.setOnClickListener(v -> copyReceptionAddress(mBinding.getOnchainAddress()));
    }
    return mBinding.getRoot();
  }

  @Override
  public void onResume() {
    super.onResume();
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this);
    }
    if (mBinding.getPaymentType() == 1) {
      refreshLightningPaneState();
    }
    if (mBinding.getPaymentType() == 0 && mBinding.getOnchainAddress() == null && getApp() != null && getApp().getElectrumState() != null) {
      setOnchainAddress(getApp().getElectrumState().onchainAddress);
    }
  }

  @Override
  public void onPause() {
    EventBus.getDefault().unregister(this);
    if (mPRParamsDialog != null) {
      mPRParamsDialog.dismiss();
    }
    super.onPause();
  }

  /**
   * Checks if current payment request has been paid.
   */
  private boolean isCurrentPaymentRequestPaid() {
    if (this.lightningPaymentHash != null && getApp() != null) {
      final Payment p = getApp().getDBHelper().getPayment(this.lightningPaymentHash, PaymentType.BTC_LN);
      if (p != null && p.getStatus() == PaymentStatus.PAID) {
        return true;
      }
    }
    return false;
  }

  private void refreshLightningPaneState() {
    if (mBinding.getPaymentType() == 1) {
      // -- check if you can receive with lightning
      mBinding.setHasNormalChannels(NodeSupervisor.hasOneNormalChannel());
      mBinding.setHasNoLightningChannels(NodeSupervisor.getChannelsMap().isEmpty());
      // -- check amount and update max receivable
      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
      mBinding.setIsLightningInboundEnabled(prefs.getBoolean(Constants.SETTING_ENABLE_LIGHTNING_INBOUND_PAYMENTS, false));
      mBinding.lightningMaxReceivable.setText(getString(R.string.receivepayment_lightning_max_receivable,
        CoinUtils.formatAmountInUnit(NodeSupervisor.getMaxReceivable(), WalletUtils.getPreferredCoinUnit(prefs), true)));
      checkLightningAmount();
      // -- if no payment request is being generated and we should have one, generate one
      if (NodeSupervisor.hasOneNormalChannel() && !NodeSupervisor.getChannelsMap().isEmpty() && mBinding.getIsLightningInboundEnabled()
        && !mBinding.getIsGeneratingLightningPR()) {
        if (this.lightningPaymentRequest == null || isCurrentPaymentRequestPaid()) {
          resetPaymentRequestFields();
          generatePaymentRequest();
        }
      }
    }
  }

  private void generatePaymentRequest() {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
    if (prefs.getBoolean(Constants.SETTING_ENABLE_LIGHTNING_INBOUND_PAYMENTS, false) && !mBinding.getIsGeneratingLightningPR()) {
      mBinding.setIsGeneratingLightningPR(true);
      if (lightningUseDefaultDescription) {
        lightningDescription = prefs.getString(Constants.SETTING_PAYMENT_REQUEST_DEFAULT_DESCRIPTION, "");
      }
      loadingPaymentRequestFields();
      log.debug("starting to generate payment request...");
      AsyncTask.execute(() -> {
        try {
          final PaymentRequest paymentRequest = Objects.requireNonNull(getApp())
            .generatePaymentRequest(lightningDescription, lightningAmount, Long.parseLong(Objects.requireNonNull(prefs.getString(Constants.SETTING_PAYMENT_REQUEST_EXPIRY, "3600"))));
          log.debug("successfully generated payment_request, starting processing");
          final String paymentRequestStr = PaymentRequest.write(paymentRequest);
          final String description = paymentRequest.description().isLeft() ? paymentRequest.description().left().get() : paymentRequest.description().right().get().toString();
          log.debug("payment request serialized to=" + paymentRequestStr);

          if (getApp() != null && getApp().getDBHelper() != null) {
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
            getApp().getDBHelper().insertOrUpdatePayment(newPayment);
          }

          this.lightningPaymentRequest = paymentRequestStr;
          this.lightningPaymentHash = paymentRequest.paymentHash().toString();
          this.lightningDescription = description;
          this.lightningAmount = paymentRequest.amount();

          if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
              checkLightningAmount();
              updateLightningDescriptionView();
              updateLightningAmountView();
              mBinding.lightningPr.setText(paymentRequestStr);
              mBinding.lightningSharePr.setOnClickListener(v -> {
                final Intent shareIntent = new Intent(android.content.Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.receivepayment_lightning_share_subject));
                shareIntent.putExtra(Intent.EXTRA_TEXT, "lightning:" + paymentRequestStr);
                startActivity(Intent.createChooser(shareIntent, getString(R.string.receivepayment_lightning_share)));
              });
              mBinding.lightningQr.setOnClickListener(v -> copyReceptionAddress(paymentRequestStr));
            });
          }

          new LightningQRCodeTask(this, paymentRequestStr, 280, 280).execute();
        } catch (Throwable t) {
          log.error("could not generate payment request", t);
          mBinding.setIsGeneratingLightningPR(false);
          if (getActivity() != null) {
            getActivity().runOnUiThread(this::failPaymentRequestFields);
          }
        }
      });
    }
  }

  private void setOnchainAddress(final String address) {
    if (address != null && !address.equals(mBinding.getOnchainAddress())) {
      new QRCodeTask(this, address, 170, 170).execute();
    }
    mBinding.setOnchainAddress(address);
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
    if (getActivity() != null && address != null) {
      try {
        ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Bitcoin payment request", address));
        Toast.makeText(getActivity().getApplicationContext(), "Copied to clipboard!", Toast.LENGTH_SHORT).show();
      } catch (Exception e) {
        log.error("failed to copy with cause=" + e.getMessage());
      }
    }
  }

  @Override
  public void processLightningQRCodeFinish(Bitmap output) {
    mBinding.setIsGeneratingLightningPR(false);
    if (output != null) {
      mBinding.lightningQr.setImageBitmap(output);
    }
  }

  private void loadingPaymentRequestFields() {
    mBinding.setExcessiveLightningAmount(false);
    mBinding.lightningPr.setText(R.string.receivepayment_lightning_wait);
    mBinding.lightningQr.setImageDrawable(getResources().getDrawable(R.drawable.qrcode_placeholder));
  }

  private void failPaymentRequestFields() {
    mBinding.lightningPr.setText(R.string.receivepayment_lightning_error);
    resetPaymentRequestFields();
  }

  private void resetPaymentRequestFields() {
    this.lightningPaymentRequest = null;
    this.lightningPaymentHash = null;
    this.lightningDescription = PreferenceManager.getDefaultSharedPreferences(this.getContext())
      .getString(Constants.SETTING_PAYMENT_REQUEST_DEFAULT_DESCRIPTION, "");
    this.lightningAmount = Option.apply(null);
    updateLightningDescriptionView();
    updateLightningAmountView();
    mBinding.lightningQr.setImageDrawable(getResources().getDrawable(R.drawable.qrcode_placeholder));
  }

  @Override
  public void onConfirm(PaymentRequestParametersDialog dialog, String description, Option<MilliSatoshi> amount) {
    this.lightningDescription = description;
    this.lightningAmount = amount;
    this.lightningUseDefaultDescription = false; // value from dialog always overrides default value
    dialog.dismiss();
    generatePaymentRequest();
  }

  private void updateLightningDescriptionView() {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getContext());
    final String defaultDesc = prefs.getString(Constants.SETTING_PAYMENT_REQUEST_DEFAULT_DESCRIPTION, "");
    if (this.lightningDescription != null && this.lightningDescription.equals(defaultDesc)) {
      mBinding.lightningDescription.setVisibility(View.GONE);
      mBinding.lightningDescriptionLabel.setVisibility(View.GONE);
    } else {
      mBinding.lightningDescription.setVisibility(View.VISIBLE);
      mBinding.lightningDescriptionLabel.setVisibility(View.VISIBLE);
      if (this.lightningDescription == null || this.lightningDescription.length() == 0) {
        mBinding.lightningDescription.setText(getString(R.string.receivepayment_lightning_description_notset));
        if (getContext() != null) {
          mBinding.lightningDescription.setTextColor(ContextCompat.getColor(getContext(), R.color.grey_2));
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
  }

  private void updateLightningAmountView() {
    if (this.lightningAmount == null || this.lightningAmount.isEmpty()) {
      mBinding.lightningAmount.setVisibility(View.GONE);
      mBinding.lightningAmountLabel.setVisibility(View.GONE);
    } else {
      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getContext());
      mBinding.lightningAmount.setVisibility(View.VISIBLE);
      mBinding.lightningAmountLabel.setVisibility(View.VISIBLE);
      mBinding.lightningAmount.setText(Html.fromHtml(getString(R.string.receivepayment_lightning_amount_value,
        CoinUtils.formatAmountInUnit(this.lightningAmount.get(), WalletUtils.getPreferredCoinUnit(prefs), true),
        WalletUtils.convertMsatToFiatWithUnit(this.lightningAmount.get().amount(), WalletUtils.getPreferredFiat(prefs)))));
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
  public void handleNewWalletAddress(final ElectrumWallet.NewWalletReceiveAddress addressEvent) {
    setOnchainAddress(addressEvent.address());
  }

  private void checkLightningAmount() {
    if (this.lightningAmount.isDefined()) {
      mBinding.setExcessiveLightningAmount(this.lightningAmount.get().$greater(NodeSupervisor.getMaxReceivable()));
    } else {
      mBinding.setExcessiveLightningAmount(false);
    }
  }

  public void notifyChannelsUpdate() {
    if (mBinding != null) {
      refreshLightningPaneState();
    }
  }
}
