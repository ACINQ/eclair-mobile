/*
 * Copyright 2019 ACINQ SAS
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
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.common.base.Strings;
import fr.acinq.eclair.MilliSatoshi;
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

import java.text.NumberFormat;

public class PaymentItemHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

  public static final String EXTRA_PAYMENT_ID = BuildConfig.APPLICATION_ID + ".PAYMENT_ID";

  private final ImageView mPaymentIcon;
  private final TextView mDescription;
  private final TextView mFeesPrefix;
  private final TextView mFees;
  private final TextView mFeesUnit;
  private final TextView mStatus;
  private final TextView mDate;
  private final TextView mAmountValue;
  private final TextView mAmountUnit;
  private Payment mPayment;

  public PaymentItemHolder(final View itemView) {
    super(itemView);
    this.mPaymentIcon = itemView.findViewById(R.id.paymentitem_type);
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
  public void bindPaymentItem(final int position, final Payment payment, final String fiatCode, final CoinUnit prefUnit, final boolean displayAmountAsFiat) {
    this.mPayment = payment;

    // override item padding set in xml file ; if item is first in list, top padding is a bit larger because it looks
    // better with the current layout.
    final int topPadding = itemView.getResources().getDimensionPixelOffset(position == 0 ? R.dimen.space_md : R.dimen.space_sm);
    final int bottomPadding = itemView.getResources().getDimensionPixelOffset(R.dimen.space_sm);
    itemView.setPadding(0, topPadding, 0, bottomPadding);

    // amount should be the amount paid, fallback to requested (useful for LN)
    final long amountMsat = payment.getAmountPaidMsat() == 0 ? payment.getAmountSentMsat() : payment.getAmountPaidMsat();
    // Adding a "-" prefix to the amount if this is an outgoing payment
    final String amountPrefix = PaymentDirection.SENT.equals(payment.getDirection()) ? "-" : "+";

    // setting amount & unit with optional conversion to fiat
    if (displayAmountAsFiat) {
      WalletUtils.printAmountInView(mAmountValue, WalletUtils.formatMsatToFiat(amountMsat, fiatCode), amountPrefix);
      mAmountUnit.setText(fiatCode.toUpperCase());
      mFees.setText(WalletUtils.formatMsatToFiat(payment.getFeesPaidMsat(), fiatCode));
      mFeesUnit.setText(fiatCode.toUpperCase());
    } else {
      WalletUtils.printAmountInView(mAmountValue, CoinUtils.formatAmountInUnit(new MilliSatoshi(amountMsat), prefUnit, false), amountPrefix);
      mAmountUnit.setText(prefUnit.shortLabel());
      mFees.setText(NumberFormat.getInstance().format((new MilliSatoshi(payment.getFeesPaidMsat())).truncateToSatoshi().toLong()));
      mFeesUnit.setText(Constants.SATOSHI_CODE);
    }

    // amount text color and fees/amount visibility depend on payment's direction and status
    if (PaymentDirection.RECEIVED.equals(payment.getDirection())) {
      mAmountValue.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green));
      mAmountValue.setVisibility(View.VISIBLE);
      mAmountUnit.setVisibility(View.VISIBLE);
      mFeesPrefix.setVisibility(View.GONE);
      mFees.setVisibility(View.GONE);
      mFeesUnit.setVisibility(View.GONE);
    } else {
      mAmountValue.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.red_faded));
      if (PaymentType.BTC_LN.equals(payment.getType()) && !PaymentStatus.PAID.equals(payment.getStatus())) {
        mAmountValue.setVisibility(View.GONE);
        mAmountUnit.setVisibility(View.GONE);
        mFeesPrefix.setVisibility(View.GONE);
        mFees.setVisibility(View.GONE);
        mFeesUnit.setVisibility(View.GONE);
      } else {
        mAmountValue.setVisibility(View.VISIBLE);
        mAmountUnit.setVisibility(View.VISIBLE);
        mFeesPrefix.setVisibility(View.VISIBLE);
        mFees.setVisibility(View.VISIBLE);
        mFeesUnit.setVisibility(View.VISIBLE);
      }
    }

    mDescription.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.grey_4));
    if (mPayment.getStatus() == PaymentStatus.FAILED) {
      mPaymentIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(itemView.getContext(), R.color.grey_1)));
    } else {
      mPaymentIcon.setImageTintList(null);
    }

    // -- lightning payments
    if (PaymentType.BTC_LN.equals(payment.getType())) {

      // -- date
      if (payment.getUpdated() != null) {
        final long delaySincePayment = payment.getUpdated().getTime() - System.currentTimeMillis();
        mDate.setText(DateUtils.getRelativeTimeSpanString(payment.getUpdated().getTime(), System.currentTimeMillis(), delaySincePayment));
      }

      // -- description
      if (Strings.isNullOrEmpty(payment.getDescription())) {
        mDescription.setText(itemView.getResources().getString(R.string.unknown_desc));
        mDescription.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
      } else {
        mDescription.setText(payment.getDescription());
        mDescription.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
      }

      // -- status
      if (PaymentDirection.SENT.equals(payment.getDirection()) && !PaymentStatus.PAID.equals(payment.getStatus())) {
        if (PaymentStatus.FAILED.equals(payment.getStatus())) {
          mStatus.setText(itemView.getContext().getString(R.string.paymentitem_failed));
          mStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.grey_1));
        } else if (PaymentStatus.PENDING.equals(payment.getStatus())) {
          mStatus.setText(itemView.getContext().getString(R.string.paymentitem_pending));
          mStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.orange));
        } else if (PaymentStatus.INIT.equals(payment.getStatus())) {
          mStatus.setText(itemView.getContext().getString(R.string.paymentitem_init));
          mStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.orange));
        }
        mStatus.setVisibility(View.VISIBLE);
      } else {
        mStatus.setVisibility(View.GONE);
      }
      // -- icon
      mPaymentIcon.setImageResource(R.drawable.ic_bolt_blue_14dp);
    }

    // -- on-chain payments
    else {

      // -- status always hidden for on-chain txs
      mStatus.setVisibility(View.GONE);

      // -- date
      if (payment.getConfidenceBlocks() == 0) {
        mDate.setText(itemView.getContext().getString(R.string.paymentitem_zero_conf));
      } else if (payment.getCreated() != null) {
        final long delaySincePayment = payment.getCreated().getTime() - System.currentTimeMillis();
        mDate.setText(DateUtils.getRelativeTimeSpanString(payment.getCreated().getTime(), System.currentTimeMillis(), delaySincePayment));
      }

      mPaymentIcon.setImageResource(R.drawable.ic_chain_orange_14dp);
      mDescription.setText(payment.getReference());
      mDescription.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
    }
  }

}
