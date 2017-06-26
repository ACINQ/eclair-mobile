package fr.acinq.eclair.swordfish.adapters;

import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import java.text.DateFormat;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.activity.PaymentDetailsActivity;
import fr.acinq.eclair.swordfish.model.Payment;
import fr.acinq.eclair.swordfish.utils.CoinFormat;

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
  private Context context;

  public PaymentItemHolder(Context context, View itemView) {
    super(itemView);
    this.context = context;
    this.amount = (TextView) itemView.findViewById(R.id.payment_item_amount);
    this.status = (TextView) itemView.findViewById(R.id.payment_item_status);
    this.description = (TextView) itemView.findViewById(R.id.payment_item_description);
    this.date = (TextView) itemView.findViewById(R.id.payment_item_date);
    itemView.setOnClickListener(this);
  }

  @Override
  public void onClick(View v) {
    Intent intent = new Intent(this.context, PaymentDetailsActivity.class);
    intent.putExtra(EXTRA_PAYMENT_ID, payment.getId().longValue());
    this.context.startActivity(intent);
  }

  public void bindPaymentItem(Payment payment) {
    this.payment = payment;
    try {
      amount.setText(CoinFormat.getMilliBTCFormat().format(package$.MODULE$.millisatoshi2millibtc(new MilliSatoshi(Long.parseLong(payment.amountPaid))).amount()));
    } catch (Exception e) {
      amount.setText(CoinFormat.getMilliBTCFormat().format(0));
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
