package fr.acinq.eclair.swordfish.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.model.ChannelItem;
import fr.acinq.eclair.swordfish.utils.CoinFormat;

public class ChannelListItemAdapter extends ArrayAdapter<ChannelItem> {
  public ChannelListItemAdapter(Context context, List<ChannelItem> channel) {
    super(context, 0, channel);
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    ChannelItem channel = getItem(position);
    if (convertView == null) {
      convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_channel, parent, false);
    }
    TextView id = (TextView) convertView.findViewById(R.id.channelitem__value_channelid);
    TextView status = (TextView) convertView.findViewById(R.id.channelitem__value_status);
    TextView balance = (TextView) convertView.findViewById(R.id.channelitem__value_balance);
    TextView targetnode = (TextView) convertView.findViewById(R.id.channelitem__value_targetnode);

    id.setText(channel.id);
    status.setText(String.valueOf(channel.status));
    balance.setText(CoinFormat.getMilliBTCFormat().format(channel.balanceSat / 100000));
    targetnode.setText(channel.targetPubkey);
    return convertView;
  }
}
