package fr.acinq.eclair.wallet.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.models.NetworkNodeItem;

public class NetworkNodeHolder extends RecyclerView.ViewHolder {
  private final TextView id;
  private final TextView alias;

  public NetworkNodeHolder(View itemView) {
    super(itemView);
    this.id = (TextView) itemView.findViewById(R.id.networknodeitem_pubkey);
    this.alias = (TextView) itemView.findViewById(R.id.networknodeitem_alias);
  }

  public void bindItem(NetworkNodeItem node) {
    id.setText(node.nodeId.toString());
    alias.setText(node.alias);
  }
}
