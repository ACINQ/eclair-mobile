package fr.acinq.eclair.swordfish.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;

import fr.acinq.bitcoin.MilliBtc;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.model.Payment;
import fr.acinq.eclair.swordfish.utils.CoinFormat;
import scala.math.BigDecimal;

public class PaymentListItemAdapter extends ArrayAdapter<Payment> {
  public PaymentListItemAdapter(Context context, List<Payment> payments) {
    super(context, 0, payments);
  }

  private static DateFormat df = DateFormat.getDateTimeInstance();

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
    status.setText(payment.status);
    date.setText(df.format(payment.updated));
    try {
      BigDecimal amount_mbtc = package$.MODULE$.millisatoshi2millibtc(PaymentRequest.read(payment.paymentRequest).amount()).amount();
      amount.setText(CoinFormat.getMilliBTCFormat().format(amount_mbtc));
    } catch (Exception e) {
      amount.setText(CoinFormat.getMilliBTCFormat().format(0));
    }
    return convertView;
  }
}
