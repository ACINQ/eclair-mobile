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
import fr.acinq.eclair.swordfish.adapters.NetworkNodeItemAdapter;
import fr.acinq.eclair.swordfish.model.NetworkNodeItem;
import fr.acinq.eclair.wire.NodeAnnouncement;

public class NetworkNodesActivity extends AppCompatActivity {

  NetworkNodeItemAdapter adapter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_network_nodes);

    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);
  }

  @Override
  public void onStart() {
    EventBus.getDefault().register(this);
    super.onStart();
    this.adapter = new NetworkNodeItemAdapter(getNodes());
    RecyclerView listView = (RecyclerView) findViewById(R.id.networknodes__list);
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
    adapter.update(getNodes());
  }

  private List<NetworkNodeItem> getNodes() {
    List<NetworkNodeItem> list = new ArrayList<>();
    for (NodeAnnouncement na : EclairEventService.nodeAnnouncementMap.values()) {
      list.add(new NetworkNodeItem(na.nodeId(), na.alias()));
    }
    if (list.isEmpty()) {
      findViewById(R.id.networknodes__label_empty).setVisibility(View.VISIBLE);
      findViewById(R.id.networknodes__list).setVisibility(View.GONE);
    } else {
      findViewById(R.id.networknodes__label_empty).setVisibility(View.GONE);
      findViewById(R.id.networknodes__list).setVisibility(View.VISIBLE);
    }
    Log.i("NetworkNodesList", "found " + list.size() + "nodes in network");
    return list;
  }
}
