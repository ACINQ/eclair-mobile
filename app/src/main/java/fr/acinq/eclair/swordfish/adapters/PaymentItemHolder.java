package fr.acinq.eclair.swordfish.adapters;

import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import java.text.DateFormat;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.activity.PaymentDetailsActivity;
import fr.acinq.eclair.swordfish.model.Payment;
import fr.acinq.eclair.swordfish.utils.CoinUtils;

public class PaymentItemHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

  public static final String EXTRA_PAYMENT_ID = "fr.acinq.eclair.swordfish.PAYMENT_ID";
  private static final int FAILED_PAYMENT_COLOR = 0xFFCD1E56;
  private static final int PENDING_PAYMENT_COLOR = 0xFFFFB81C;
  private static final int SUCCESS_PAYMENT_COLOR = 0xFF00C28C;
  private final TextView description;
  private final TextView status;
  private final TextView date;
  private final TextView amount;
  private Payment payment;

  public PaymentItemHolder(View itemView) {
    super(itemView);
    this.amount = (TextView) itemView.findViewById(R.id.paymentitem_amount_value);
    this.status = (TextView) itemView.findViewById(R.id.paymentitem_status);
    this.description = (TextView) itemView.findViewById(R.id.paymentitem_description);
    this.date = (TextView) itemView.findViewById(R.id.paymentitem_date);
    itemView.setOnClickListener(this);
  }

  @Override
  public void onClick(View v) {
    Intent intent = new Intent(v.getContext(), PaymentDetailsActivity.class);
    intent.putExtra(EXTRA_PAYMENT_ID, payment.getId().longValue());
    v.getContext().startActivity(intent);
  }

  public void bindPaymentItem(Payment payment) {
    this.payment = payment;
    try {
      if (payment.amountPaid == 0) {
        amount.setText(CoinUtils.formatAmountMilliBtc(new MilliSatoshi(payment.amountRequested)));
      } else {
        amount.setText(CoinUtils.formatAmountMilliBtc(new MilliSatoshi(payment.amountPaid)));
      }
    } catch (Exception e) {
      amount.setText(CoinUtils.getMilliBTCFormat().format(0));
    }
    this.status.setText(payment.status);
    if ("FAILED".equals(payment.status)) {
      status.setTextColor(FAILED_PAYMENT_COLOR);
    } else if ("PAID".equals(payment.status)) {
      status.setTextColor(SUCCESS_PAYMENT_COLOR);
    } else {
      status.setTextColor(PENDING_PAYMENT_COLOR);
    }
    this.description.setText(payment.description);
    date.setText(DateFormat.getDateTimeInstance().format(payment.updated));
  }

}
