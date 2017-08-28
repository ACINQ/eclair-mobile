package fr.acinq.eclair.wallet.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.models.NetworkChannelItem;

public class NetworkChannelItemAdapter extends RecyclerView.Adapter<NetworkChannelHolder> {

  private List<NetworkChannelItem> channels;

  public NetworkChannelItemAdapter(List<NetworkChannelItem> channels) {
    this.channels = channels;
  }

  @Override
  public NetworkChannelHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_network_channel, parent, false);
    return new NetworkChannelHolder(view);
  }

  @Override
  public void onBindViewHolder(NetworkChannelHolder holder, int position) {
    NetworkChannelItem channel = this.channels.get(position);
    holder.bindNetworkChannelItem(channel);
  }

  @Override
  public int getItemCount() {
    return this.channels == null ? 0 : this.channels.size();
  }

  public void update(List<NetworkChannelItem> channels) {
    if (channels == null) {
      this.channels = channels;
    } else {
      this.channels.clear();
      this.channels.addAll(channels);
    }
    notifyDataSetChanged();
  }
}
