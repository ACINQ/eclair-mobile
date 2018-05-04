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

package fr.acinq.eclair.wallet.adapters;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Typeface;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.common.base.Strings;

import java.text.DateFormat;
import java.text.NumberFormat;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.activities.BitcoinTransactionDetailsActivity;
import fr.acinq.eclair.wallet.activities.LNPaymentDetailsActivity;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDirection;
import fr.acinq.eclair.wallet.models.PaymentStatus;
import fr.acinq.eclair.wallet.models.PaymentType;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class PaymentItemHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

  public static final String EXTRA_PAYMENT_ID = BuildConfig.APPLICATION_ID + "PAYMENT_ID";
  private static final String TAG = "PaymentItemHolder";

  private final ImageView mPaymentIcon;
  private final TextView mDescription;
  private final TextView mFeesPrefix;
  private final TextView mFees;
  private final TextView mFeesUnit;
  private final TextView mStatus;
  private final TextView mDate;
  private final View mAmountView;
  private final TextView mAmountValue;
  private final TextView mAmountUnit;
  private Payment mPayment;

  public PaymentItemHolder(final View itemView) {
    super(itemView);
    this.mPaymentIcon = itemView.findViewById(R.id.paymentitem_image);
    this.mAmountView = itemView.findViewById(R.id.paymentitem_amount);
    this.mAmountValue = itemView.findViewById(R.id.paymentitem_amount_value);
    this.mAmountUnit = itemView.findViewById(R.id.paymentitem_amount_unit);
    this.mStatus = itemView.findViewById(R.id.paymentitem_status);
    this.mDescription = itemView.findViewById(R.id.paymentitem_description);
    this.mDate = itemView.findViewById(R.id.paymentitem_date);
    this.mFeesPrefix = itemView.findViewById(R.id.paymentitem_fees_prefix);
    this.mFees = itemView.findViewById(R.id.paymentitem_fees_value);
    this.mFeesUnit = itemView.findViewById(R.id.paymentitem_fees_unit);
    itemView.setOnClickListener(this);
  }

  @Override
  public void onClick(final View v) {
    final Intent intent = PaymentType.BTC_LN.equals(mPayment.getType())
      ? new Intent(v.getContext(), LNPaymentDetailsActivity.class)
      : new Intent(v.getContext(), BitcoinTransactionDetailsActivity.class);
    intent.putExtra(EXTRA_PAYMENT_ID, mPayment.getId().longValue());
    v.getContext().startActivity(intent);
  }

  @SuppressLint("SetTextI18n")
  public void bindPaymentItem(final Payment payment, final String fiatCode, final CoinUnit prefUnit, final boolean displayAmountAsFiat) {
    this.mPayment = payment;

    // amount should be the amount paid, fallback to requested (useful for LN)
    final long amountMsat = payment.getAmountPaidMsat() == 0 ? payment.getAmountSentMsat() : payment.getAmountPaidMsat();
    // Adding a "-" prefix to the amount if this is an outgoing payment
    final String amountPrefix = PaymentDirection.SENT.equals(payment.getDirection()) ? "-" : "";

    // setting amount & unit with optional conversion to fiat
    if (displayAmountAsFiat) {
      mAmountValue.setText(amountPrefix + WalletUtils.convertMsatToFiat(amountMsat, fiatCode));
      mFees.setText(WalletUtils.convertMsatToFiat(payment.getFeesPaidMsat(), fiatCode));
      mFeesUnit.setText(fiatCode.toUpperCase());
      mAmountUnit.setText(fiatCode.toUpperCase());
    } else {
      mAmountValue.setText(amountPrefix + CoinUtils.formatAmountInUnit(new MilliSatoshi(amountMsat), prefUnit, false));
      mAmountUnit.setText(prefUnit.shortLabel());
      mFees.setText(NumberFormat.getInstance().format(package$.MODULE$.millisatoshi2satoshi(new MilliSatoshi(payment.getFeesPaidMsat())).amount()));
      mFeesUnit.setText(Constants.SATOSHI_CODE);
    }

    // Fees display & amount text color depends on payment direction
    if (PaymentDirection.RECEIVED.equals(payment.getDirection())) {
      mAmountValue.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green));
      mFeesPrefix.setVisibility(View.GONE);
      mFees.setVisibility(View.GONE);
      mFeesUnit.setVisibility(View.GONE);
    } else {
      mAmountValue.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.red_faded));
      mFeesPrefix.setVisibility(View.VISIBLE);
      mFees.setVisibility(View.VISIBLE);
      mFeesUnit.setVisibility(View.VISIBLE);
    }
    mDescription.setTypeface(Typeface.DEFAULT);
    mDescription.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.grey_4));

    if (PaymentType.BTC_LN.equals(payment.getType())) {
      if (payment.getUpdated() != null) {
        mDate.setText(DateFormat.getDateTimeInstance().format(payment.getUpdated()));
      }
      if (Strings.isNullOrEmpty(payment.getDescription())) {
        mDescription.setText(itemView.getResources().getString(R.string.unknown_desc));
        mDescription.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
        mDescription.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.grey_1));
      } else {
        mDescription.setText(payment.getDescription());
      }
      mStatus.setText(payment.getStatus().name());
      if (PaymentStatus.FAILED.equals(payment.getStatus())) {
        mStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.red_faded));
      } else if (PaymentStatus.PAID.equals(payment.getStatus())) {
        mStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green));
      } else {
        mStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.orange));
      }
      mPaymentIcon.setImageResource(R.mipmap.ic_bolt_circle);
    } else {
      if (payment.getCreated() != null) {
        mDate.setText(DateFormat.getDateTimeInstance().format(payment.getCreated()));
      }
      // convention: negative number of confirmations means conflicted
      if (payment.getConfidenceBlocks() >= 0) {
        // text
        final String confidenceBlocks = payment.getConfidenceBlocks() < 13 ?
          Integer.toString(payment.getConfidenceBlocks()) : "12+";
        mStatus.setText(confidenceBlocks + " " + itemView.getResources().getString(R.string.paymentitem_confidence_suffix));
        // color: green above 2
        if (payment.getConfidenceBlocks() < 3) {
          mStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.grey_2));
        } else {
          mStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green));
        }
      } else {
        mStatus.setText("In conflict");
        mStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.red_faded));
      }
      mPaymentIcon.setImageResource(R.mipmap.ic_bitcoin_circle);
      mDescription.setText(payment.getReference());
    }
  }

}
