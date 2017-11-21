package fr.acinq.eclair.wallet.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.utils.Constants;

public class PaymentListItemAdapter extends RecyclerView.Adapter<PaymentItemHolder> {

  private static final String TAG = "PaymentAdapter";
  private List<Payment> payments;
  private String fiatCode = Constants.FIAT_EURO;
  private String prefUnit = Constants.MILLI_BTC_CODE;
  private boolean displayAmountAsFiat = false; // by default always show amounts in bitcoin

  public PaymentListItemAdapter(List<Payment> payments) {
    this.payments = payments;
  }

  @Override
  public PaymentItemHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_payment, parent, false);
    return new PaymentItemHolder(view);
  }

  @Override
  public void onBindViewHolder(PaymentItemHolder holder, int position) {
    final Payment payment = this.payments.get(position);
    holder.bindPaymentItem(payment, this.fiatCode, this.prefUnit, this.displayAmountAsFiat);
  }

  @Override
  public int getItemCount() {
    return this.payments == null ? 0 : this.payments.size();
  }

  public void update(final List<Payment> payments, final String fiatCode, final String prefUnit, final boolean displayAmountAsFiat) {
    this.fiatCode = fiatCode;
    this.prefUnit = prefUnit;
    this.displayAmountAsFiat = displayAmountAsFiat;
    if (payments == null) {
      this.payments = payments;
    } else {
      this.payments.clear();
      this.payments.addAll(payments);
    }
    notifyDataSetChanged();
  }
}
