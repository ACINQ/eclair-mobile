package fr.acinq.eclair.swordfish.adapters;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.model.Payment;

public class PaymentListItemAdapter extends RecyclerView.Adapter<PaymentItemHolder> {

  private final List<Payment> payments;
  private Context context;

  public PaymentListItemAdapter(Context context, List<Payment> payments) {
    this.payments = payments;
    this.context = context;
  }

  @Override
  public PaymentItemHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_payment, parent, false);
    return new PaymentItemHolder(this.context, view);
  }

  @Override
  public void onBindViewHolder(PaymentItemHolder holder, int position) {
    Payment payment = this.payments.get(position);
    holder.bindPaymentItem(payment);
  }

  @Override
  public int getItemCount() {
    return this.payments.size();
  }

}
