package fr.acinq.eclair.wallet.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.models.Payment;

public class PaymentListItemAdapter extends RecyclerView.Adapter<PaymentItemHolder> {

  private List<Payment> payments;

  public PaymentListItemAdapter(List<Payment> payments) {
    this.payments = payments;
  }

  @Override
  public PaymentItemHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_payment, parent, false);
    return new PaymentItemHolder(view);
  }

  @Override
  public void onBindViewHolder(PaymentItemHolder holder, int position) {
    Payment payment = this.payments.get(position);
    holder.bindPaymentItem(payment);
  }

  @Override
  public int getItemCount() {
    return this.payments == null ? 0 : this.payments.size();
  }

  public void update(List<Payment> payments) {
    if (payments == null) {
      this.payments = payments;
    } else {
      this.payments.clear();
      this.payments.addAll(payments);
    }
    notifyDataSetChanged();
  }
}
