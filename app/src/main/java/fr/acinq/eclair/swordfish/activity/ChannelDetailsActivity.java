package fr.acinq.eclair.swordfish.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.util.Map;

import akka.actor.ActorRef;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.eclair.channel.CLOSED;
import fr.acinq.eclair.channel.CLOSING;
import fr.acinq.eclair.channel.CMD_CLOSE;
import fr.acinq.eclair.swordfish.EclairEventService;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.adapters.LocalChannelItemHolder;
import fr.acinq.eclair.swordfish.customviews.DataRow;
import fr.acinq.eclair.swordfish.utils.CoinUtils;

public class ChannelDetailsActivity extends AppCompatActivity {

  private static final String TAG = "ChannelDetailsActivity";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_channel_details);

    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);
  }

  @Override
  protected void onStart() {
    super.onStart();
    Intent intent = getIntent();
    String channelId = intent.getStringExtra(LocalChannelItemHolder.EXTRA_CHANNEL_ID);
    try {
      final Map.Entry<ActorRef, EclairEventService.ChannelDetails> channel = getChannel(channelId);

      if (channel.getValue() != null && channel.getKey() != null) {
        DataRow idRow = (DataRow) findViewById(R.id.channeldetails_id);
        idRow.setValue(channel.getValue().channelId);
        DataRow balanceRow = (DataRow) findViewById(R.id.channeldetails_balance);
        balanceRow.setValue(CoinUtils.formatAmountMilliBtc(channel.getValue().balanceMsat));
        DataRow capacityRow = (DataRow) findViewById(R.id.channeldetails_capacity);
        capacityRow.setValue(CoinUtils.formatAmountMilliBtc(channel.getValue().capacityMsat));
        DataRow nodeIdRow = (DataRow) findViewById(R.id.channeldetails_nodeid);
        nodeIdRow.setValue(channel.getValue().remoteNodeId);
        DataRow stateRow = (DataRow) findViewById(R.id.channeldetails_state);
        stateRow.setValue(channel.getValue().state);
        DataRow transactionIdRow = (DataRow) findViewById(R.id.channeldetails_transactionid);
        transactionIdRow.setValue(channel.getValue().transactionId);

        Button closeButton = (Button) findViewById(R.id.channeldetails_close);
        if (!CLOSING.toString().equals(channel.getValue().state) && !CLOSED.toString().equals(channel.getValue().state)) {
          closeButton.setVisibility(View.VISIBLE);
          closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              scala.Option<BinaryData> none = scala.Option.apply(null);
              channel.getKey().tell(CMD_CLOSE.apply(none), channel.getKey());
            }
          });
        }
      }

    } catch (Exception e) {
      Log.e(TAG, "Internal error", e);
      goToHome();
    }
  }

  private void goToHome() {
    Intent homeIntent = new Intent(this, HomeActivity.class);
    startActivity(homeIntent);
  }

  private Map.Entry<ActorRef, EclairEventService.ChannelDetails> getChannel(String channelId) {
    for (Map.Entry<ActorRef, EclairEventService.ChannelDetails> e : EclairEventService.getChannelsMap().entrySet()) {
      if (e.getValue().channelId.equals(channelId)) {
        return e;
      }
    }
    return null;
  }
}
