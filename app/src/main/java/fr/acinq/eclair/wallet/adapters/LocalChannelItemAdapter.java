package fr.acinq.eclair.wallet.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.models.ChannelItem;

public class LocalChannelItemAdapter extends RecyclerView.Adapter<LocalChannelItemHolder> {

  private List<ChannelItem> channels;

  public LocalChannelItemAdapter(List<ChannelItem> channels) {
    this.channels = channels;
  }

  @Override
  public LocalChannelItemHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_local_channel, parent, false);
    return new LocalChannelItemHolder(view);
  }

  @Override
  public void onBindViewHolder(LocalChannelItemHolder holder, int position) {
    ChannelItem channels = this.channels.get(position);
    holder.bindItem(channels);
  }

  @Override
  public int getItemCount() {
    return this.channels == null ? 0 : this.channels.size();
  }

  public void update(List<ChannelItem> channels) {
    if (channels == null) {
      this.channels = channels;
    } else {
      this.channels.clear();
      this.channels.addAll(channels);
    }
    notifyDataSetChanged();
  }
}
