/*
 * Copyright 2019 ACINQ SAS
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
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.Globals;
import fr.acinq.eclair.channel.CLOSED$;
import fr.acinq.eclair.channel.CLOSING$;
import fr.acinq.eclair.channel.NORMAL$;
import fr.acinq.eclair.channel.OFFLINE$;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.activities.ChannelDetailsActivity;
import fr.acinq.eclair.wallet.models.ClosingType;
import fr.acinq.eclair.wallet.models.LocalChannel;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class LocalChannelItemHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

  public static final String EXTRA_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".CHANNEL_ID";

  private final TextView balance;
  private final TextView balanceUnit;
  private final TextView node;
  private final TextView state;
  private final TextView delayedClosing;
  private final TextView inflightHtlcs;
  private final ProgressBar balanceProgressBar;
  private LocalChannel channel;

  LocalChannelItemHolder(View itemView) {
    super(itemView);
    this.state = itemView.findViewById(R.id.channelitem_state);
    this.balance = itemView.findViewById(R.id.channelitem_balance_value);
    this.node = itemView.findViewById(R.id.channelitem_node);
    this.balanceUnit = itemView.findViewById(R.id.channelitem_balance_unit);
    this.delayedClosing = itemView.findViewById(R.id.delayed_closing);
    this.inflightHtlcs = itemView.findViewById(R.id.inflight_htlcs);
    this.balanceProgressBar = itemView.findViewById(R.id.balance_progress);
    itemView.setOnClickListener(this);
  }

  @Override
  public void onClick(View v) {
    Intent intent = new Intent(v.getContext(), ChannelDetailsActivity.class);
    intent.putExtra(EXTRA_CHANNEL_ID, this.channel.getChannelId());
    v.getContext().startActivity(intent);
  }

  @SuppressLint("SetTextI18n")
  protected void bindItem(final LocalChannel item, final String fiatCode, final CoinUnit prefUnit, final boolean displayAmountAsFiat) {
    this.channel = item;
    node.setText(itemView.getResources().getString(R.string.channelitem_with_node_funder, item.getPeerNodeId()));

    // ---- setting amount & unit with optional conversion to fiat
    if (displayAmountAsFiat) {
      WalletUtils.printAmountInView(balance, WalletUtils.formatMsatToFiat(item.getBalanceMsat(), fiatCode));
      balanceUnit.setText(fiatCode.toUpperCase());
    } else {
      WalletUtils.printAmountInView(balance, CoinUtils.formatAmountInUnit(new MilliSatoshi(item.getBalanceMsat()), prefUnit, false));
      balanceUnit.setText(prefUnit.shortLabel());
    }

    if (!item.getIsActive()) {
      state.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.grey_1));
      final long delaySinceClosed = channel.getUpdated().getTime() - System.currentTimeMillis();
      state.setText(itemView.getResources().getString(R.string.channelitem_inactive_date,
        DateUtils.getRelativeTimeSpanString(channel.getUpdated().getTime(), System.currentTimeMillis(), delaySinceClosed)));
      balanceProgressBar.setVisibility(View.GONE);
    } else {
      // ---- state
      state.setText(item.state);
      if (NORMAL$.MODULE$.toString().equals(item.state)) {
        state.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green));
      } else if (OFFLINE$.MODULE$.toString().equals(item.state) || item.state.startsWith("ERR_")) {
        state.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.red_faded));
      } else if (CLOSED$.MODULE$.toString().equals(item.state)) {
        state.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.grey_1));
      } else {
        if (CLOSING$.MODULE$.toString().equals(item.state)) {
          state.setText(item.state.toUpperCase() + " " + itemView.getResources().getString(
            ClosingType.MUTUAL.equals(item.getClosingType()) ? R.string.channelitem_cooperative : R.string.channelitem_uncooperative));
        }
        state.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.orange));
      }

      // ---- additional dynamic info, such as delayed closing tx, inflight htlcs...
      if (CLOSING$.MODULE$.toString().equals(item.state) && ClosingType.LOCAL.equals(item.getClosingType())) {
        // TODO: get the exact block at which the closing tx will be broadcast
        if (item.getRefundAtBlock() > 0 && Globals.blockCount().get() > 0) {
          final long remainingBlocks = item.getRefundAtBlock() - Globals.blockCount().get();
          if (remainingBlocks > 0) {
            delayedClosing.setText(itemView.getResources().getString(R.string.channelitem_delayed_closing, remainingBlocks, remainingBlocks > 1 ? "s" : ""));
          } else {
            delayedClosing.setText(itemView.getResources().getString(R.string.channelitem_delayed_closing_claimable));
          }
        } else {
          delayedClosing.setText(itemView.getResources().getString(R.string.channelitem_delayed_closing_unknown, item.getToSelfDelayBlocks()));
        }
        delayedClosing.setVisibility(View.VISIBLE);
      } else {
        delayedClosing.setVisibility(View.GONE);
      }

      if (item.htlcsInFlightCount > 0) {
        inflightHtlcs.setText(itemView.getResources().getString(R.string.channelitem_inflight_htlcs, item.htlcsInFlightCount));
        inflightHtlcs.setVisibility(View.VISIBLE);
      } else {
        inflightHtlcs.setVisibility(View.GONE);
      }

      if (channel.getCapacityMsat() > 0) {
        final double progress = (double) channel.getBalanceMsat() / channel.getCapacityMsat() * 100;
        balanceProgressBar.setProgress((int) progress);
      }
    }
  }
}
