package fr.acinq.eclair.wallet.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.models.ChannelItem;
import fr.acinq.eclair.wallet.utils.Constants;

public class LocalChannelItemAdapter extends RecyclerView.Adapter<LocalChannelItemHolder> {

  private List<ChannelItem> channels;
  private String fiatCode = Constants.FIAT_USD;
  private String prefUnit = Constants.MILLI_BTC_CODE;
  private boolean displayAmountAsFiat = false; // by default always show amounts in bitcoin

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
    holder.bindItem(channels, this.fiatCode, this.prefUnit, this.displayAmountAsFiat);
  }

  @Override
  public int getItemCount() {
    return this.channels == null ? 0 : this.channels.size();
  }

  public void update(List<ChannelItem> channels, final String fiatCode, final String prefUnit, final boolean displayAmountAsFiat) {
    this.fiatCode = fiatCode;
    this.prefUnit = prefUnit;
    this.displayAmountAsFiat = displayAmountAsFiat;
    if (channels == null) {
      this.channels = channels;
    } else {
      this.channels.clear();
      this.channels.addAll(channels);
    }
    notifyDataSetChanged();
  }
}
