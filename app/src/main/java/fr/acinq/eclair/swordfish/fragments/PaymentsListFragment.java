package fr.acinq.eclair.swordfish.fragments;

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

import java.util.List;

import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.adapters.PaymentListItemAdapter;
import fr.acinq.eclair.swordfish.model.Payment;

public class PaymentsListFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {

  private View mView;
  private PaymentListItemAdapter mPaymentAdapter;
  private SwipeRefreshLayout mRefreshLayout;

  @Override
  public void onRefresh() {
    mPaymentAdapter.update(getPayments());
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

    mView = inflater.inflate(R.layout.fragment_paymentslist, container, false);
    mRefreshLayout = (SwipeRefreshLayout) mView.findViewById(R.id.payments_swiperefresh);
    mRefreshLayout.setColorSchemeResources(R.color.colorPrimary, R.color.green, R.color.colorAccent);
    mRefreshLayout.setOnRefreshListener(this);

    return mView;
  }

  @Override
  public void onStart() {
    super.onStart();
    this.mPaymentAdapter = new PaymentListItemAdapter(getPayments());
    RecyclerView listView = (RecyclerView) mView.findViewById(R.id.payments_list);
    listView.setHasFixedSize(true);
    listView.setLayoutManager(new LinearLayoutManager(mView.getContext()));
    listView.setAdapter(mPaymentAdapter);
  }

  private List<Payment> getPayments() {
    List<Payment> list = Payment.findWithQuery(Payment.class, "SELECT * FROM Payment ORDER BY created DESC LIMIT 30");
    TextView emptyLabel = (TextView) mView.findViewById(R.id.payments_empty);
    if (list.isEmpty()) {
      emptyLabel.setVisibility(View.VISIBLE);
    } else {
      emptyLabel.setVisibility(View.GONE);
    }
    return list;
  }

  public void updateList() {
    mPaymentAdapter.update(getPayments());
  }

}
