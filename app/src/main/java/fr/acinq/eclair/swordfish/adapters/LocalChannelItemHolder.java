package fr.acinq.eclair.swordfish.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.model.ChannelItem;
import fr.acinq.eclair.swordfish.utils.CoinUtils;

public class LocalChannelItemHolder extends RecyclerView.ViewHolder {

  private final TextView id;
  private final TextView status;
  private final TextView balance;
  private final TextView targetNode;

  public LocalChannelItemHolder(View itemView) {
    super(itemView);
    this.id = (TextView) itemView.findViewById(R.id.channelitem__value_channelid);
    this.status = (TextView) itemView.findViewById(R.id.channelitem__value_status);
    this.balance = (TextView) itemView.findViewById(R.id.channelitem__value_balance);
    this.targetNode = (TextView) itemView.findViewById(R.id.channelitem__value_targetnode);
  }

  public void bindItem(ChannelItem channelItem) {
    id.setText(channelItem.id);
    status.setText(String.valueOf(channelItem.status));
    balance.setText(CoinUtils.formatAmountMilliBtc(new MilliSatoshi(channelItem.balanceMsat)));
    targetNode.setText(channelItem.targetPubkey);
  }

}
