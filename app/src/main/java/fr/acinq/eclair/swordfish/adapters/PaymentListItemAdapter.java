package fr.acinq.eclair.swordfish.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.List;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.model.Payment;

/**
 * Created by Dominique on 18/05/2017.
 */

public class PaymentListItemAdapter extends ArrayAdapter<Payment> {
  public PaymentListItemAdapter(Context context, List<Payment> payments) {
    super(context, 0, payments);
  }

  private static DateFormat df = DateFormat.getDateInstance();

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    Payment payment = getItem(position);
    if (convertView == null) {
      convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_payment, parent, false);
    }
    TextView description = (TextView) convertView.findViewById(R.id.payment_item_description);
    TextView status = (TextView) convertView.findViewById(R.id.payment_item_status);
    TextView date = (TextView) convertView.findViewById(R.id.payment_item_date);
    TextView amount = (TextView) convertView.findViewById(R.id.payment_item_amount);

    description.setText("".equals(payment.description) ? "N/A" : payment.description);
    status.setText(String.valueOf(payment.status));
    date.setText(df.format(payment.updated));
    try {
      amount.setText(NumberFormat.getInstance().format(PaymentRequest.read(payment.paymentRequest).amount().amount() / 1000));
    } catch (Exception e) {
      amount.setText(NumberFormat.getInstance().format(0));
    }
    return convertView;
  }
}
