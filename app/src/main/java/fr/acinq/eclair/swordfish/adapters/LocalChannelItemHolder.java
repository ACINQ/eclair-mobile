package fr.acinq.eclair.swordfish.adapters;

import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.activity.ChannelDetailsActivity;
import fr.acinq.eclair.swordfish.model.ChannelItem;
import fr.acinq.eclair.swordfish.utils.CoinUtils;

public class LocalChannelItemHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

  public static final String EXTRA_CHANNEL_ID = "fr.acinq.eclair.swordfish.CHANNEL_ID";

  private final TextView id;
  private final TextView status;
  private final TextView balance;
  private final TextView targetNode;
  private ChannelItem channelItem;

  public LocalChannelItemHolder(View itemView) {
    super(itemView);
    this.id = (TextView) itemView.findViewById(R.id.channelitem__value_channelid);
    this.status = (TextView) itemView.findViewById(R.id.channelitem__value_status);
    this.balance = (TextView) itemView.findViewById(R.id.channelitem__value_balance);
    this.targetNode = (TextView) itemView.findViewById(R.id.channelitem__value_targetnode);
  }

  @Override
  public void onClick(View v) {
    Intent intent = new Intent(v.getContext(), ChannelDetailsActivity.class);
    intent.putExtra(EXTRA_CHANNEL_ID, this.channelItem.id);
    v.getContext().startActivity(intent);
  }

  public void bindItem(final ChannelItem channelItem) {
    this.channelItem = channelItem;
    id.setText(channelItem.id);
    status.setText(String.valueOf(channelItem.status));
    balance.setText(CoinUtils.formatAmountMilliBtc(channelItem.balanceMsat));
    targetNode.setText(channelItem.targetPubkey);
  }
}
