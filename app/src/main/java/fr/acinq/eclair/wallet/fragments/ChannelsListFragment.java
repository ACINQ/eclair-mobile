package fr.acinq.eclair.wallet.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
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

import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.wallet.EclairEventService;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.adapters.LocalChannelItemAdapter;
import fr.acinq.eclair.wallet.models.ChannelItem;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class ChannelsListFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {

  private View mView;
  private LocalChannelItemAdapter mChannelAdapter;
  private SwipeRefreshLayout mRefreshLayout;
  private TextView mEmptyLabel;

  @Override
  public void onRefresh() {
    updateList();
    mRefreshLayout.setRefreshing(false);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(false);
    mChannelAdapter = new LocalChannelItemAdapter(new ArrayList<>());
  }

  @Override
  public void onResume() {
    super.onResume();
    updateList();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    mView = inflater.inflate(R.layout.fragment_channelslist, container, false);
    mRefreshLayout = mView.findViewById(R.id.localchannels_swiperefresh);
    mRefreshLayout.setColorSchemeResources(R.color.primary, R.color.green, R.color.accent);
    mRefreshLayout.setOnRefreshListener(this);
    mEmptyLabel = mView.findViewById(R.id.localchannels_empty);

    final RecyclerView listView = mView.findViewById(R.id.localchannels_list);
    listView.setHasFixedSize(true);
    listView.setLayoutManager(new LinearLayoutManager(mView.getContext()));
    listView.setAdapter(mChannelAdapter);

    return mView;
  }

  private List<ChannelItem> getChannels() {
    List<ChannelItem> items = new ArrayList<>();
    for (EclairEventService.ChannelDetails d : EclairEventService.getChannelsMap().values()) {
      ChannelItem item = new ChannelItem(d.channelId, d.capacityMsat, d.remoteNodeId);
      if (d.state == null) {
        item.state = "UNKNOWN";
      } else {
        item.state = d.state;
        item.isCooperativeClosing = d.isCooperativeClosing;
      }
      item.balanceMsat = d.balanceMsat;
      items.add(item);
    }
    if (mEmptyLabel != null) {
      if (items.isEmpty()) {
        mEmptyLabel.setVisibility(View.VISIBLE);
      } else {
        mEmptyLabel.setVisibility(View.GONE);
      }
    }
    return items;
  }

  public void updateList() {
    if (mChannelAdapter != null && getContext() != null) {
      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
      final CoinUnit prefUnit = WalletUtils.getPreferredCoinUnit(prefs);
      final String fiatCode = WalletUtils.getPreferredFiat(prefs);
      final boolean displayBalanceAsFiat = WalletUtils.shouldDisplayInFiat(prefs);
      mChannelAdapter.update(getChannels(), fiatCode, prefUnit, displayBalanceAsFiat);
    }
  }
}
