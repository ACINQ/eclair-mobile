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

import android.annotation.SuppressLint;
import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.channel.CLOSING$;
import fr.acinq.eclair.channel.NORMAL$;
import fr.acinq.eclair.channel.OFFLINE$;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.activities.ChannelDetailsActivity;
import fr.acinq.eclair.wallet.models.ChannelItem;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class LocalChannelItemHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

  public static final String EXTRA_CHANNEL_ID = BuildConfig.APPLICATION_ID + "CHANNEL_ID";

  private final TextView state;
  private final TextView balance;
  private final TextView balanceUnit;
  private final TextView node;
  private ChannelItem channelItem;

  public LocalChannelItemHolder(View itemView) {
    super(itemView);
    this.state = itemView.findViewById(R.id.channelitem_state);
    this.balance = itemView.findViewById(R.id.channelitem_balance_value);
    this.node = itemView.findViewById(R.id.channelitem_node);
    this.balanceUnit = itemView.findViewById(R.id.channelitem_balance_unit);
    itemView.setOnClickListener(this);
  }

  @Override
  public void onClick(View v) {
    Intent intent = new Intent(v.getContext(), ChannelDetailsActivity.class);
    intent.putExtra(EXTRA_CHANNEL_ID, this.channelItem.id);
    v.getContext().startActivity(intent);
  }

  @SuppressLint("SetTextI18n")
  protected void bindItem(final ChannelItem channelItem, final String fiatCode, final CoinUnit prefUnit, final boolean displayAmountAsFiat) {
    this.channelItem = channelItem;
    state.setText(channelItem.state);
    if (NORMAL$.MODULE$.toString().equals(channelItem.state)) {
      state.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green));
    } else if (OFFLINE$.MODULE$.toString().equals(channelItem.state) || channelItem.state.startsWith("ERR_")) {
      state.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.red_faded));
    } else {
      if (CLOSING$.MODULE$.toString().equals(channelItem.state)) {
        state.setText(channelItem.state.toUpperCase() + (channelItem.isCooperativeClosing ? " (cooperative)" : " (uncooperative)"));
      }
      state.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.orange));
    }

    // setting amount & unit with optional conversion to fiat
    if (displayAmountAsFiat) {
      balance.setText(WalletUtils.convertMsatToFiat(channelItem.balanceMsat.amount(), fiatCode));
      balanceUnit.setText(fiatCode.toUpperCase());
    } else {
      balance.setText(CoinUtils.formatAmountInUnit(channelItem.balanceMsat, prefUnit, false));
      balanceUnit.setText(prefUnit.shortLabel());
    }
    node.setText("With " + channelItem.targetPubkey);
  }
}
