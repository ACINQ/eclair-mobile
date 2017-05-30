package fr.acinq.eclair.swordfish.adapters;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.model.Payment;
import fr.acinq.eclair.swordfish.model.PaymentRequest;

/**
 * Created by Dominique on 18/05/2017.
 */

public class PaymentListItemAdapter extends ArrayAdapter<Payment> {
  public PaymentListItemAdapter(Context context, List<Payment> payments) {
    super(context, 0, payments);
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    Payment payment = getItem(position);
    if (convertView == null) {
      convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_payment, parent, false);
    }
    TextView description = (TextView) convertView.findViewById(R.id.payment_item_description);
    TextView status = (TextView) convertView.findViewById(R.id.payment_item_status);
    TextView date = (TextView) convertView.findViewById(R.id.payment_item_date);

    description.setText(payment.description);
    status.setText(String.valueOf(payment.status));
    date.setText(DateFormat.format("yyyy-MM-dd", payment.updated));
    return convertView;
  }
}
