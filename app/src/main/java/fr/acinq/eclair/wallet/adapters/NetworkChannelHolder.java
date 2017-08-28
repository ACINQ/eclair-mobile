package fr.acinq.eclair.wallet.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.models.NetworkChannelItem;

public class NetworkChannelHolder  extends RecyclerView.ViewHolder {
  private final TextView id;
  private final TextView node1;
  private final TextView node2;

  public void bindNetworkChannelItem(NetworkChannelItem channel) {
    id.setText(Long.toHexString(channel.shortChannelId));
    node1.setText(channel.nodeId1.toString());
    node2.setText(channel.nodeId2.toString());
  }

  public NetworkChannelHolder(View itemView) {
    super(itemView);
    this.id = (TextView) itemView.findViewById(R.id.networkchannelitem_channelid);
    this.node1 = (TextView) itemView.findViewById(R.id.networkchannelitem_node1);
    this.node2 = (TextView) itemView.findViewById(R.id.networkchannelitem_node2);
  }
}
