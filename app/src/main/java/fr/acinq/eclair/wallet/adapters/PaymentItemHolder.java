package fr.acinq.eclair.wallet.adapters;

import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.bitcoinj.core.TransactionConfidence;

import java.text.DateFormat;
import java.text.NumberFormat;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.activities.BitcoinTransactionDetailsActivity;
import fr.acinq.eclair.wallet.activities.LNPaymentDetailsActivity;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDirection;
import fr.acinq.eclair.wallet.models.PaymentStatus;
import fr.acinq.eclair.wallet.models.PaymentType;
import fr.acinq.eclair.wallet.utils.CoinUtils;

public class PaymentItemHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

  public static final String EXTRA_PAYMENT_ID = "fr.acinq.eclair.swordfish.PAYMENT_ID";

  private final ImageView mPaymentIcon;
  private final TextView mDescription;
  private final TextView mFeesPrefix;
  private final TextView mFees;
  private final TextView mFeesUnit;
  private final TextView mStatus;
  private final TextView mDate;
  private final TextView mAmount;
  private Payment mPayment;

  public PaymentItemHolder(View itemView) {
    super(itemView);
    this.mPaymentIcon = itemView.findViewById(R.id.paymentitem_image);
    this.mAmount = itemView.findViewById(R.id.paymentitem_amount_value);
    this.mStatus = itemView.findViewById(R.id.paymentitem_status);
    this.mDescription = itemView.findViewById(R.id.paymentitem_description);
    this.mDate = itemView.findViewById(R.id.paymentitem_date);
    this.mFeesPrefix = itemView.findViewById(R.id.paymentitem_fees_prefix);
    this.mFees = itemView.findViewById(R.id.paymentitem_fees_value);
    this.mFeesUnit = itemView.findViewById(R.id.paymentitem_fees_unit);
    itemView.setOnClickListener(this);
  }

  @Override
  public void onClick(View v) {
    Intent intent = PaymentType.BTC_LN.equals(mPayment.getType())
      ? new Intent(v.getContext(), LNPaymentDetailsActivity.class)
      : new Intent(v.getContext(), BitcoinTransactionDetailsActivity.class);
    intent.putExtra(EXTRA_PAYMENT_ID, mPayment.getId().longValue());
    v.getContext().startActivity(intent);
  }

  public void bindPaymentItem(Payment payment) {
    this.mPayment = payment;

    if (payment.getUpdated() != null) {
      mDate.setText(DateFormat.getDateTimeInstance().format(payment.getUpdated()));
    }

    // Fees display & amount text color depends on payment direction
    if (PaymentDirection.RECEIVED.equals(payment.getDirection())) {
      mAmount.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green));
      mFeesPrefix.setVisibility(View.GONE);
      mFees.setVisibility(View.GONE);
      mFeesUnit.setVisibility(View.GONE);
    } else {
      mAmount.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.redFaded));
      mFees.setText(NumberFormat.getInstance().format(payment.getFeesPaidMsat() / 1000));
      mFeesPrefix.setVisibility(View.VISIBLE);
      mFees.setVisibility(View.VISIBLE);
      mFeesUnit.setVisibility(View.VISIBLE);
    }

    if (PaymentType.BTC_LN.equals(payment.getType())) {
      try {
        if (payment.getAmountPaidMsat() == 0) {
          mAmount.setText("-" + CoinUtils.formatAmountMilliBtc(new MilliSatoshi(payment.getAmountRequestedMsat())));
        } else {
          mAmount.setText("-" + CoinUtils.formatAmountMilliBtc(new MilliSatoshi(payment.getAmountPaidMsat())));
        }
      } catch (Exception e) {
        mAmount.setText(CoinUtils.getMilliBTCFormat().format(0));
      }

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

      if (payment.getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING.getValue()
        || payment.getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING.getValue()
        || payment.getConfidenceType() == TransactionConfidence.ConfidenceType.UNKNOWN.getValue()) {

        // text
        String confidenceBlocks = payment.getConfidenceBlocks() < 7 ?
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

      mAmount.setText(CoinUtils.formatAmountMilliBtc(new MilliSatoshi(payment.getAmountPaidMsat())));
    }
  }

}
