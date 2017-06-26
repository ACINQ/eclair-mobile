package fr.acinq.eclair.swordfish.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.model.NetworkChannelItem;

/**
 * Created by Dominique on 13/06/2017.
 */

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
    this.id = (TextView) itemView.findViewById(R.id.item_channelid_value);
    this.node1 = (TextView) itemView.findViewById(R.id.item_node1_value);
    this.node2 = (TextView) itemView.findViewById(R.id.item_node2_value);
  }
}
