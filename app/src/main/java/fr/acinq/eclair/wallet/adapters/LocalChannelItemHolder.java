package fr.acinq.eclair.wallet.adapters;

import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import fr.acinq.eclair.channel.CLOSING;
import fr.acinq.eclair.channel.NORMAL;
import fr.acinq.eclair.channel.OFFLINE;
import fr.acinq.eclair.wallet.EclairEventService;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.activities.ChannelDetailsActivity;
import fr.acinq.eclair.wallet.models.ChannelItem;
import fr.acinq.eclair.wallet.utils.CoinUtils;

public class LocalChannelItemHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

  public static final String EXTRA_CHANNEL_ID = "fr.acinq.eclair.swordfish.CHANNEL_ID";

  private final TextView state;
  private final TextView balance;
  private final TextView node;
  private final TextView nodeAlias;
  private ChannelItem channelItem;

  public LocalChannelItemHolder(View itemView) {
    super(itemView);
    this.state = (TextView) itemView.findViewById(R.id.channelitem_state);
    this.balance = (TextView) itemView.findViewById(R.id.channelitem_balance_value);
    this.node = (TextView) itemView.findViewById(R.id.channelitem_node);
    this.nodeAlias = (TextView) itemView.findViewById(R.id.channelitem_nodealias);
    itemView.setOnClickListener(this);
  }

  @Override
  public void onClick(View v) {
    Intent intent = new Intent(v.getContext(), ChannelDetailsActivity.class);
    intent.putExtra(EXTRA_CHANNEL_ID, this.channelItem.id);
    v.getContext().startActivity(intent);
  }

  protected void bindItem(final ChannelItem channelItem) {
    this.channelItem = channelItem;
    state.setText(channelItem.state);
    if (NORMAL.toString().equals(channelItem.state)) {
      state.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green));
    } else if (OFFLINE.toString().equals(channelItem.state) || channelItem.state.startsWith("ERR_")) {
      state.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.redFaded));
    } else {
      if (CLOSING.toString().equals(channelItem.state)) {
        state.setText(channelItem.state.toUpperCase() + (channelItem.isCooperativeClosing ? " (cooperative)" : " (uncooperative)"));
      }
      state.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.orange));
    }
    balance.setText(CoinUtils.formatAmountMilliBtc(channelItem.balanceMsat));
    String targetAlias = EclairEventService.getNodeAlias(channelItem.targetPubkey);
    if (targetAlias.length() > 0) nodeAlias.setText("(" + targetAlias + ")");
    node.setText("With " + channelItem.targetPubkey);
  }
}
