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

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.models.LightningPaymentError;

public class LightningErrorListAdapter extends RecyclerView.Adapter<LightningErrorHolder> {

  private static final String TAG = "LightningErrorListAdapter";
  private final List<LightningPaymentError> errors;

  public LightningErrorListAdapter(List<LightningPaymentError> errors) {
    this.errors = errors;
  }

  @Override
  public LightningErrorHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lightning_error, parent, false);
    return new LightningErrorHolder(view);
  }

  @Override
  public void onBindViewHolder(LightningErrorHolder holder, int position) {
    final LightningPaymentError error = this.errors.get(position);
    holder.bindErrorItem(error, position, errors.size());
  }

  @Override
  public int getItemCount() {
    return this.errors == null ? 0 : this.errors.size();
  }
}
