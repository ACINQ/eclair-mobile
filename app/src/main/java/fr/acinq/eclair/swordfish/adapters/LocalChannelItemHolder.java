package fr.acinq.eclair.swordfish.adapters;

import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import fr.acinq.eclair.channel.NORMAL;
import fr.acinq.eclair.channel.OFFLINE;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.activity.ChannelDetailsActivity;
import fr.acinq.eclair.swordfish.model.ChannelItem;
import fr.acinq.eclair.swordfish.utils.CoinUtils;

public class LocalChannelItemHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

  public static final String EXTRA_CHANNEL_ID = "fr.acinq.eclair.swordfish.CHANNEL_ID";

  private static final int ACTIVE_COLOR = 0xFF00C28C;
  private static final int OFFLINE_COLOR = 0xFFCD1E56;
  private static final int WAITING_COLOR = 0xFFFFB81C;

  private final TextView status;
  private final TextView balance;
  private final TextView node;
  private ChannelItem channelItem;

  public LocalChannelItemHolder(View itemView) {
    super(itemView);
    this.status = (TextView) itemView.findViewById(R.id.channelitem_status);
    this.balance = (TextView) itemView.findViewById(R.id.channelitem_balance);
    this.node = (TextView) itemView.findViewById(R.id.channelitem_node);
    itemView.setOnClickListener(this);
  }

  @Override
  public void onClick(View v) {
    Intent intent = new Intent(v.getContext(), ChannelDetailsActivity.class);
    intent.putExtra(EXTRA_CHANNEL_ID, this.channelItem.id);
    v.getContext().startActivity(intent);
  }

  public void bindItem(final ChannelItem channelItem) {
    this.channelItem = channelItem;
    status.setText(channelItem.status);
    if (NORMAL.toString().equals(channelItem.status)) {
      status.setTextColor(ACTIVE_COLOR);
    } else if (OFFLINE.toString().equals(channelItem.status) || channelItem.status.startsWith("ERR_")) {
      status.setTextColor(OFFLINE_COLOR);
    } else {
      status.setTextColor(WAITING_COLOR);
    }
    balance.setText(CoinUtils.formatAmountMilliBtc(channelItem.balanceMsat));
    node.setText("With " + channelItem.targetPubkey);
  }
}
