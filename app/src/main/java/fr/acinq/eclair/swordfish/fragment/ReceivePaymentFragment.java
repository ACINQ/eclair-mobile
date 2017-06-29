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

public class ReceivePaymentFragment extends Fragment {

  private View mView;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(false);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    mView = inflater.inflate(R.layout.fragment_receive_payment, container, false);

    return mView;
  }

  @Override
  public void onStart() {
    super.onStart();
  }
}
