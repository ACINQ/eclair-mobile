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
import fr.acinq.eclair.wallet.models.LocalChannel;
import fr.acinq.eclair.wallet.utils.Constants;

public class LocalChannelItemAdapter extends RecyclerView.Adapter<LocalChannelItemHolder> {

  private List<LocalChannel> channels;
  private String fiatCode = Constants.FIAT_USD;
  private CoinUnit prefUnit = CoinUtils.getUnitFromString(Constants.BTC_CODE);
  private boolean displayAmountAsFiat = false; // by default always show amounts in bitcoin

  public LocalChannelItemAdapter(List<LocalChannel> channels) {
    this.channels = channels;
  }

  @Override
  public LocalChannelItemHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_local_channel, parent, false);
    return new LocalChannelItemHolder(view);
  }

  @Override
  public void onBindViewHolder(LocalChannelItemHolder holder, int position) {
    holder.bindItem(this.channels.get(position), this.fiatCode, this.prefUnit, this.displayAmountAsFiat);
  }

  @Override
  public int getItemCount() {
    return this.channels == null ? 0 : this.channels.size();
  }

  public void update(List<LocalChannel> channels, final String fiatCode, final CoinUnit prefUnit, final boolean displayAmountAsFiat) {
    this.fiatCode = fiatCode;
    this.prefUnit = prefUnit;
    this.displayAmountAsFiat = displayAmountAsFiat;
    if (channels == null) {
      this.channels = channels;
    } else {
      this.channels.clear();
      this.channels.addAll(channels);
    }
    notifyDataSetChanged();
  }
}
