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

import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.adapters.PaymentListItemAdapter;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDao;
import fr.acinq.eclair.wallet.utils.CoinUtils;

public class PaymentsListFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {

  private static final String TAG = "PaymentListFrag";
  private View mView;
  private PaymentListItemAdapter mPaymentAdapter;
  private SwipeRefreshLayout mRefreshLayout;
  private TextView mEmptyLabel;
  private SharedPreferences.OnSharedPreferenceChangeListener prefListener;

  @Override
  public void onRefresh() {
    updateList();
    mRefreshLayout.setRefreshing(false);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(false);
    mPaymentAdapter = new PaymentListItemAdapter(new ArrayList<Payment>());
  }

  @Override
  public void onResume() {
    super.onResume();
    if (getActivity() != null && prefListener != null) {
      PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext()).registerOnSharedPreferenceChangeListener(prefListener);
    }
    updateList();
  }

  @Override
  public void onPause() {
    super.onPause();
    if (getActivity() != null && prefListener != null) {
      PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext()).unregisterOnSharedPreferenceChangeListener(prefListener);
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    mView = inflater.inflate(R.layout.fragment_paymentslist, container, false);
    mRefreshLayout = mView.findViewById(R.id.payments_swiperefresh);
    mRefreshLayout.setColorSchemeResources(R.color.primary, R.color.green, R.color.accent);
    mRefreshLayout.setOnRefreshListener(this);
    mEmptyLabel = mView.findViewById(R.id.payments_empty);

    RecyclerView listView = mView.findViewById(R.id.payments_list);
    listView.setHasFixedSize(true);
    listView.setLayoutManager(new LinearLayoutManager(mView.getContext()));
    listView.setAdapter(mPaymentAdapter);

    return mView;
  }

  /**
   * Fetches the last 100 payments from DB, ordered by update date (desc).
   * <p>
   * TODO seek + infinite scroll
   *
   * @return list of payments
   */
  private List<Payment> getPayments() {

    if (getActivity() == null || getActivity().getApplication() == null || ((App) getActivity().getApplication()).getDBHelper() == null) return new ArrayList<>();

    final List<Payment> list = ((App) getActivity().getApplication()).getDBHelper().getDaoSession().getPaymentDao()
      .queryBuilder().orderDesc(PaymentDao.Properties.Updated).limit(100).list();

    if (mEmptyLabel != null) {
      if (list.isEmpty()) {
        mEmptyLabel.setVisibility(View.VISIBLE);
      } else {
        mEmptyLabel.setVisibility(View.GONE);
      }
    }
    return list;
  }

  public void refreshList() {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
    final String prefUnit = CoinUtils.getBtcPreferredUnit(prefs);
    final String fiatCode = CoinUtils.getPreferredFiat(prefs);
    final boolean displayBalanceAsFiat = CoinUtils.shouldDisplayInFiat(prefs);
    mPaymentAdapter.update(fiatCode, prefUnit, displayBalanceAsFiat);
  }

  public void updateList() {
    if (getContext() != null) {
      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
      final String prefUnit = CoinUtils.getBtcPreferredUnit(prefs);
      final String fiatCode = CoinUtils.getPreferredFiat(prefs);
      final boolean displayBalanceAsFiat = CoinUtils.shouldDisplayInFiat(prefs);
      mPaymentAdapter.update(getPayments(), fiatCode, prefUnit, displayBalanceAsFiat);
    }
  }
}

