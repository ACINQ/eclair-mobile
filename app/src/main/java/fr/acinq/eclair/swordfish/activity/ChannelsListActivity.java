package fr.acinq.eclair.swordfish.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.util.Timeout;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.eclair.swordfish.ChannelsListTask;
import fr.acinq.eclair.swordfish.EclairHelper;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.adapters.ChannelListItemAdapter;
import fr.acinq.eclair.swordfish.adapters.PaymentListItemAdapter;
import fr.acinq.eclair.swordfish.model.ChannelItem;
import fr.acinq.eclair.swordfish.model.Payment;
import scala.Symbol;
import scala.collection.Iterable;
import scala.collection.Iterator;
import scala.collection.JavaConversions;
import scala.collection.immutable.Map;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.collection.JavaConverters.*;

public class ChannelsListActivity extends AppCompatActivity implements ChannelsListTask.AsyncChannelsListResponse {

  @Override
  public void processFinish(List<ChannelItem> channels) {
    if (channels.size() == 0) {
      findViewById(R.id.channelslist__label_empty).setVisibility(View.VISIBLE);
      findViewById(R.id.channelslist__listview).setVisibility(View.GONE);
    } else {
      ChannelListItemAdapter adapter = new ChannelListItemAdapter(this, channels);
      ListView listView = (ListView) findViewById(R.id.channelslist__listview);
      listView.setAdapter(adapter);
      findViewById(R.id.channelslist__label_empty).setVisibility(View.GONE);
      findViewById(R.id.channelslist__listview).setVisibility(View.VISIBLE);
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_channels_list);
    new ChannelsListTask(this, getApplicationContext()).execute();
  }

  public void channelslist__refresh(View view) {
    new ChannelsListTask(this, getApplicationContext()).execute();
  }

  public void channellist__goToFund(View view) {
    Intent intent = new Intent(this, FundActivity.class);
    startActivity(intent);
  }
}
