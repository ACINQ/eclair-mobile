package fr.acinq.eclair.swordfish.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.model.NetworkNodeItem;

public class NetworkNodeHolder extends RecyclerView.ViewHolder {
  private final TextView id;
  private final TextView alias;
  private NetworkNodeItem node;

  public NetworkNodeHolder(View itemView) {
    super(itemView);
    this.id = (TextView) itemView.findViewById(R.id.item_nodeId_value);
    this.alias = (TextView) itemView.findViewById(R.id.item_nodeAlias_value);
  }

  public void bindItem(NetworkNodeItem node) {
    this.node = node;
    id.setText(node.nodeId.toString());
    alias.setText(node.alias);
  }
}
