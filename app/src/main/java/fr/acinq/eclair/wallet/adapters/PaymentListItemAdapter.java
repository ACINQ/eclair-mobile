/*
 * Copyright 2018 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.eclair.wallet.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.utils.Constants;

public class PaymentListItemAdapter extends RecyclerView.Adapter<PaymentItemHolder> {

  private static final String TAG = "PaymentAdapter";
  private List<Payment> payments;
  private String fiatCode = Constants.FIAT_USD;
  private CoinUnit prefUnit = CoinUtils.getUnitFromString("btc");
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

  public void update(final String fiatCode, final CoinUnit prefUnit, final boolean displayAmountAsFiat) {
    update(this.payments, fiatCode, prefUnit, displayAmountAsFiat);
  }

  public void update(final List<Payment> payments, final String fiatCode, final CoinUnit prefUnit, final boolean displayAmountAsFiat) {
    this.fiatCode = fiatCode;
    this.prefUnit = prefUnit;
    this.displayAmountAsFiat = displayAmountAsFiat;
    if (payments == null) {
      this.payments = payments;
    } else if (!this.payments.equals(payments)) {
      this.payments.clear();
      this.payments.addAll(payments);
    }
    notifyDataSetChanged();
  }
}
