package fr.acinq.eclair.swordfish.activity;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import fr.acinq.eclair.swordfish.EclairEventService;
import fr.acinq.eclair.swordfish.events.NetworkAnnouncementEvent;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.adapters.NetworkChannelItemAdapter;
import fr.acinq.eclair.swordfish.model.NetworkChannelItem;
import fr.acinq.eclair.wire.ChannelAnnouncement;

public class NetworkChannelsActivity extends AppCompatActivity {

  NetworkChannelItemAdapter adapter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_network_channels);

    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);
  }

  @Override
  public void onStart() {
    EventBus.getDefault().register(this);
    super.onStart();
    this.adapter = new NetworkChannelItemAdapter(this, getChannels());
    RecyclerView listView = (RecyclerView) findViewById(R.id.networkchannels__list);
    listView.setHasFixedSize(true);
    listView.setLayoutManager(new LinearLayoutManager(this));
    listView.setAdapter(adapter);
  }

  @Override
  public void onStop() {
    EventBus.getDefault().unregister(this);
    super.onStop();
  }

  @Override
  public void onPause() {
    EventBus.getDefault().unregister(this);
    super.onPause();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onMessageEvent(NetworkAnnouncementEvent event) {
    adapter.update(getChannels());
  }

  private List<NetworkChannelItem> getChannels() {
    List<NetworkChannelItem> list = new ArrayList<>();
    for (ChannelAnnouncement ca : EclairEventService.channelAnnouncementMap.values()) {
      list.add(new NetworkChannelItem(ca.shortChannelId(), ca.nodeId1(), ca.nodeId2()));
    }
    if (list.isEmpty()) {
      findViewById(R.id.networkchannels__label_empty).setVisibility(View.VISIBLE);
      findViewById(R.id.networkchannels__list).setVisibility(View.GONE);
    } else {
      findViewById(R.id.networkchannels__label_empty).setVisibility(View.GONE);
      findViewById(R.id.networkchannels__list).setVisibility(View.VISIBLE);
    }
    Log.i("NetworkChannelsList", "found " + list.size() + "channels in network");
    return list;
  }
}
