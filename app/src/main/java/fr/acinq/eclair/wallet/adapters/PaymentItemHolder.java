package fr.acinq.eclair.wallet.adapters;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.NumberFormat;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.activities.BitcoinTransactionDetailsActivity;
import fr.acinq.eclair.wallet.activities.LNPaymentDetailsActivity;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDirection;
import fr.acinq.eclair.wallet.models.PaymentStatus;
import fr.acinq.eclair.wallet.models.PaymentType;
import fr.acinq.eclair.wallet.utils.CoinUtils;
import fr.acinq.eclair.wallet.utils.Constants;

public class PaymentItemHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

  public static final String EXTRA_PAYMENT_ID = "fr.acinq.eclair.swordfish.PAYMENT_ID";
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
  public void bindPaymentItem(final Payment payment, final String fiatCode, final String prefUnit, final boolean displayAmountAsFiat) {
    this.mPayment = payment;
    Log.d(TAG, "bind payment display fiat=" + displayAmountAsFiat);
    if (payment.getUpdated() != null) {
      mDate.setText(DateFormat.getDateTimeInstance().format(payment.getUpdated()));
    }

    // amount should be the amount paid, fallback to requested (useful for LN)
    final long amountMsat = payment.getAmountPaidMsat() == 0 ? payment.getAmountRequestedMsat() : payment.getAmountPaidMsat();
    // Adding a "-" prefix to the amount if this is an outgoing payment
    final String amountPrefix = PaymentDirection.SENT.equals(payment.getDirection()) ? "-" : "";

    // setting amount & unit with optional conversion to fiat
    if (displayAmountAsFiat) {
      mAmountValue.setText(amountPrefix + CoinUtils.convertMsatToFiat(amountMsat, fiatCode));
      mFees.setText(CoinUtils.convertMsatToFiat(payment.getFeesPaidMsat(), fiatCode));
      mFeesUnit.setText(fiatCode.toUpperCase());
      mAmountUnit.setText(fiatCode.toUpperCase());
    } else {
      mAmountValue.setText(amountPrefix + CoinUtils.formatAmountInUnit(new MilliSatoshi(amountMsat), prefUnit));
      mAmountUnit.setText(CoinUtils.getShortLabel(prefUnit));
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
      mAmountValue.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.redFaded));
      mFeesPrefix.setVisibility(View.VISIBLE);
      mFees.setVisibility(View.VISIBLE);
      mFeesUnit.setVisibility(View.VISIBLE);
    }

    if (PaymentType.BTC_LN.equals(payment.getType())) {
      mDescription.setText(payment.getDescription());
      mStatus.setText(payment.getStatus().name());
      if (PaymentStatus.FAILED.equals(payment.getStatus())) {
        mStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.redFaded));
      } else if (PaymentStatus.PAID.equals(payment.getStatus())) {
        mStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green));
      } else {
        mStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.orange));
      }
      mPaymentIcon.setImageResource(R.mipmap.ic_bolt_circle);
    } else {
      // convention: negative number of confirmations means conflicted
      if (payment.getConfidenceBlocks() >= 0) {
        // text
        final String confidenceBlocks = payment.getConfidenceBlocks() < 7 ?
          Integer.toString(payment.getConfidenceBlocks()) : "6+";
        mStatus.setText(confidenceBlocks + " " + itemView.getResources().getString(R.string.paymentitem_confidence_suffix));
        // color: green above 2
        if (payment.getConfidenceBlocks() < 2) {
          mStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.colorGrey_2));
        } else {
          mStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green));
        }
      } else {
        mStatus.setText("In conflict");
        mStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.redFaded));
      }
      mPaymentIcon.setImageResource(R.mipmap.ic_bitcoin_circle);
      mDescription.setText(payment.getReference());
    }
  }

}
