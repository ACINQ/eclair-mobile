package fr.acinq.eclair.swordfish.fragment;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import fr.acinq.eclair.swordfish.EclairEventService;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.adapters.LocalChannelItemAdapter;
import fr.acinq.eclair.swordfish.model.ChannelItem;

public class ChannelsListFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {

  private View mView;
  private LocalChannelItemAdapter mChannelAdapter;
  private SwipeRefreshLayout mRefreshLayout;

  @Override
  public void onRefresh() {
    mChannelAdapter.update(getChannels());
    mRefreshLayout.setRefreshing(false);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(false);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    mView = inflater.inflate(R.layout.fragment_channelslist, container, false);

    mRefreshLayout = (SwipeRefreshLayout) mView.findViewById(R.id.localchannels_swiperefresh);
    mRefreshLayout.setColorSchemeResources(R.color.colorPrimary, R.color.green, R.color.colorAccent);
    mRefreshLayout.setOnRefreshListener(this);
    return mView;
  }

  @Override
  public void onStart() {
    super.onStart();
    this.mChannelAdapter = new LocalChannelItemAdapter(getChannels());
    RecyclerView listView = (RecyclerView) mView.findViewById(R.id.localchannels_list);
    listView.setHasFixedSize(true);
    listView.setLayoutManager(new LinearLayoutManager(mView.getContext()));
    listView.setAdapter(mChannelAdapter);
  }

  private List<ChannelItem> getChannels() {
    List<ChannelItem> items = new ArrayList<>();
    for (EclairEventService.ChannelDetails d : EclairEventService.getChannelsMap().values()) {
      ChannelItem item = new ChannelItem(d.channelId, d.capacityMsat, d.remoteNodeId);
      if (d.state == null) {
        item.status = "UNKNOWN";
      } else {
        item.status = d.state;
      }
      item.balanceMsat = d.balanceMsat;
      items.add(item);
    }
    TextView emptyLabel = (TextView) mView.findViewById(R.id.localchannels_empty);
    if (items.isEmpty()) {
      emptyLabel.setVisibility(View.VISIBLE);
    } else {
      emptyLabel.setVisibility(View.GONE);
    }
    return items;
  }

  public void updateList() {
    mChannelAdapter.update(getChannels());
  }
}
