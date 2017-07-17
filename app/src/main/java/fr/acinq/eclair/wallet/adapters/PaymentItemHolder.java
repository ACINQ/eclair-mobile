package fr.acinq.eclair.wallet.adapters;

import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.bitcoinj.core.TransactionConfidence;

import java.text.DateFormat;
import java.text.NumberFormat;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.activity.BitcoinTransactionDetailsActivity;
import fr.acinq.eclair.wallet.activity.LNPaymentDetailsActivity;
import fr.acinq.eclair.wallet.model.Payment;
import fr.acinq.eclair.wallet.model.PaymentTypes;
import fr.acinq.eclair.wallet.utils.CoinUtils;

public class PaymentItemHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

  public static final String EXTRA_PAYMENT_ID = "fr.acinq.eclair.swordfish.PAYMENT_ID";
  private static final int FAILED_PAYMENT_COLOR = 0xFFCD1E56;
  private static final int PENDING_PAYMENT_COLOR = 0xFFFFB81C;
  private static final int SUCCESS_PAYMENT_COLOR = 0xFF00C28C;
  private final ImageView mPaymentIcon;
  private final TextView mDescription;
  private final TextView mFees;
  private final TextView mFeesUnit;
  private final TextView mStatus;
  private final TextView mDate;
  private final TextView mAmount;
  private Payment mPayment;

  public PaymentItemHolder(View itemView) {
    super(itemView);
    this.mPaymentIcon = (ImageView) itemView.findViewById(R.id.paymentitem_image);
    this.mAmount = (TextView) itemView.findViewById(R.id.paymentitem_amount_value);
    this.mStatus = (TextView) itemView.findViewById(R.id.paymentitem_status);
    this.mDescription = (TextView) itemView.findViewById(R.id.paymentitem_description);
    this.mDate = (TextView) itemView.findViewById(R.id.paymentitem_date);
    this.mFees = (TextView) itemView.findViewById(R.id.paymentitem_fees_value);
    this.mFeesUnit = (TextView) itemView.findViewById(R.id.paymentitem_fees_unit);
    itemView.setOnClickListener(this);
  }

  @Override
  public void onClick(View v) {
    Intent intent = PaymentTypes.LN.toString().equals(mPayment.type) ? new Intent(v.getContext(), LNPaymentDetailsActivity.class) : new Intent(v.getContext(), BitcoinTransactionDetailsActivity.class);
    intent.putExtra(EXTRA_PAYMENT_ID, mPayment.getId().longValue());
    v.getContext().startActivity(intent);
  }

  public void bindPaymentItem(Payment payment) {
    this.mPayment = payment;

    if (payment.updated != null) {
      mDate.setText(DateFormat.getDateTimeInstance().format(payment.updated));
    }

    if (!PaymentTypes.BTC_RECEIVED.toString().equals(payment.type)) {
      mFees.setText(NumberFormat.getInstance().format(payment.feesPaidMsat / 1000));
      mFees.setVisibility(View.VISIBLE);
      mFeesUnit.setVisibility(View.VISIBLE);
    } else {
      mFees.setVisibility(View.GONE);
      mFeesUnit.setVisibility(View.GONE);
    }


    if (PaymentTypes.LN.toString().equals(payment.type)) {
      try {
        if (payment.amountPaidMsat == 0) {
          mAmount.setText("-" + CoinUtils.formatAmountMilliBtc(new MilliSatoshi(payment.amountRequestedMsat)));
        } else {
          mAmount.setText("-" + CoinUtils.formatAmountMilliBtc(new MilliSatoshi(payment.amountPaidMsat)));
        }
      } catch (Exception e) {
        mAmount.setText(CoinUtils.getMilliBTCFormat().format(0));
      }
      mAmount.setTextColor(itemView.getResources().getColor(R.color.redFaded));
      mDescription.setText(payment.description);
      mStatus.setVisibility(View.VISIBLE);
      mStatus.setText(payment.status);
      if ("FAILED".equals(payment.status)) {
        mStatus.setTextColor(FAILED_PAYMENT_COLOR);
      } else if ("PAID".equals(payment.status)) {
        mStatus.setTextColor(SUCCESS_PAYMENT_COLOR);
      } else {
        mStatus.setTextColor(PENDING_PAYMENT_COLOR);
      }
      mPaymentIcon.setImageResource(R.drawable.icon_bolt_circle_blue);
    } else {
      mStatus.setVisibility(View.VISIBLE);

      if (payment.confidenceType == TransactionConfidence.ConfidenceType.BUILDING.getValue()
        || payment.confidenceType == TransactionConfidence.ConfidenceType.PENDING.getValue()
        || payment.confidenceType == TransactionConfidence.ConfidenceType.UNKNOWN.getValue()) {

        String confidenceBlocks = payment.confidenceBlocks < 7 ? Integer.toString(payment.confidenceBlocks) : "6+";
        mStatus.setText(confidenceBlocks + " " + itemView.getResources().getString(R.string.paymentitem_confidence_suffix));
        if (payment.confidenceBlocks < 2) {
          mStatus.setTextColor(itemView.getResources().getColor(R.color.colorGrey_2));
        } else {
          mStatus.setTextColor(itemView.getResources().getColor(R.color.green));
        }

      } else {
        mStatus.setText("In conflict");
        mStatus.setTextColor(FAILED_PAYMENT_COLOR);
      }
      mPaymentIcon.setImageResource(R.drawable.icon_btc_extrude_orange);
      mDescription.setText(payment.paymentReference);
      if (PaymentTypes.BTC_RECEIVED.toString().equals(payment.type)) {
        mAmount.setText(CoinUtils.formatAmountMilliBtc(new MilliSatoshi(payment.amountPaidMsat)));
        mAmount.setTextColor(itemView.getResources().getColor(R.color.green));
      } else if (PaymentTypes.BTC_SENT.toString().equals(payment.type)) {
        mAmount.setText(CoinUtils.formatAmountMilliBtc(new MilliSatoshi(payment.amountPaidMsat)));
        mAmount.setTextColor(itemView.getResources().getColor(R.color.redFaded));
      }
    }
  }

}
