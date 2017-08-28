package fr.acinq.eclair.wallet.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.models.NetworkNodeItem;

public class NetworkNodeItemAdapter extends RecyclerView.Adapter<NetworkNodeHolder> {

  private List<NetworkNodeItem> nodes;

  public NetworkNodeItemAdapter(List<NetworkNodeItem> nodes) {
    this.nodes = nodes;
  }

  @Override
  public NetworkNodeHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_network_node, parent, false);
    return new NetworkNodeHolder(view);
  }

  @Override
  public void onBindViewHolder(NetworkNodeHolder holder, int position) {
    NetworkNodeItem node = this.nodes.get(position);
    holder.bindItem(node);
  }

  @Override
  public int getItemCount() {
    return this.nodes == null ? 0 : this.nodes.size();
  }

  public void update(List<NetworkNodeItem> nodes) {
    if (nodes == null) {
      this.nodes = nodes;
    } else {
      this.nodes.clear();
      this.nodes.addAll(nodes);
    }
    notifyDataSetChanged();
  }
}
