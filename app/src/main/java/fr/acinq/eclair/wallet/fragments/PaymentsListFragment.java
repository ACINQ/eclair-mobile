/*
 * Copyright 2018 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

import org.greenrobot.greendao.query.QueryBuilder;

import java.util.ArrayList;
import java.util.List;

import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.adapters.PaymentListItemAdapter;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDao;
import fr.acinq.eclair.wallet.models.PaymentStatus;
import fr.acinq.eclair.wallet.models.PaymentType;
import fr.acinq.eclair.wallet.utils.WalletUtils;

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
    mPaymentAdapter = new PaymentListItemAdapter(new ArrayList<>());
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
    final QueryBuilder<Payment> qb = ((App) getActivity().getApplication()).getDBHelper().getDaoSession().getPaymentDao().queryBuilder();
    qb.whereOr(
      PaymentDao.Properties.Type.eq(PaymentType.BTC_ONCHAIN),
      qb.and(PaymentDao.Properties.Type.eq(PaymentType.BTC_LN), PaymentDao.Properties.Status.notEq(PaymentStatus.INIT)));
    qb.orderDesc(PaymentDao.Properties.Updated).orderAsc(PaymentDao.Properties.ConfidenceBlocks).limit(100);
    final List<Payment> list = qb.list();

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
    final CoinUnit prefUnit = WalletUtils.getPreferredCoinUnit(prefs);
    final String fiatCode = WalletUtils.getPreferredFiat(prefs);
    final boolean displayBalanceAsFiat = WalletUtils.shouldDisplayInFiat(prefs);
    mPaymentAdapter.update(fiatCode, prefUnit, displayBalanceAsFiat);
  }

  public void updateList() {
    if (getContext() != null) {
      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
      final CoinUnit prefUnit = WalletUtils.getPreferredCoinUnit(prefs);
      final String fiatCode = WalletUtils.getPreferredFiat(prefs);
      final boolean displayBalanceAsFiat = WalletUtils.shouldDisplayInFiat(prefs);
      mPaymentAdapter.update(getPayments(), fiatCode, prefUnit, displayBalanceAsFiat);
    }
  }
}

