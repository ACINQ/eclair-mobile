/*
 * Copyright 2019 ACINQ SAS
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.greendao.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.adapters.PaymentListItemAdapter;
import fr.acinq.eclair.wallet.events.BalanceUpdateEvent;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDao;
import fr.acinq.eclair.wallet.models.PaymentDirection;
import fr.acinq.eclair.wallet.models.PaymentStatus;
import fr.acinq.eclair.wallet.models.PaymentType;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class PaymentsListFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {
  private final Logger log = LoggerFactory.getLogger(PaymentsListFragment.class);
  private View mView;
  private PaymentListItemAdapter mPaymentAdapter;
  private SwipeRefreshLayout mRefreshLayout;
  private TextView mEmptyLabel;
  private ConstraintLayout mSunsetNotice;
  private View.OnClickListener onNoticeClick = null;

  @Override
  public void onRefresh() {
    updateList();
    EventBus.getDefault().post(new BalanceUpdateEvent());
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
    updateList();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    mView = inflater.inflate(R.layout.fragment_paymentslist, container, false);
    mRefreshLayout = mView.findViewById(R.id.payments_swiperefresh);
    mRefreshLayout.setColorSchemeResources(R.color.primary, R.color.green, R.color.accent);
    mRefreshLayout.setOnRefreshListener(this);
    mEmptyLabel = mView.findViewById(R.id.payments_empty);
    mSunsetNotice = mView.findViewById(R.id.sunset_notice_layout);
    mSunsetNotice.setOnClickListener(onNoticeClick);

    RecyclerView listView = mView.findViewById(R.id.payments_list);
    listView.setHasFixedSize(true);
    listView.setLayoutManager(new LinearLayoutManager(mView.getContext()));
    listView.setAdapter(mPaymentAdapter);

    return mView;
  }

  /**
   * Fetches the last 150 payments from DB, ordered by update date (desc).
   * <p>
   * TODO seek + infinite scroll
   *
   * @return list of payments
   */
  private List<Payment> getPayments() {
    if (getActivity() == null || getActivity().getApplication() == null || ((App) getActivity().getApplication()).getDBHelper() == null) {
      return new ArrayList<>();
    }
    final QueryBuilder<Payment> qb = ((App) getActivity().getApplication()).getDBHelper().getDaoSession().getPaymentDao().queryBuilder();
    qb.whereOr(
      PaymentDao.Properties.Type.eq(PaymentType.BTC_ONCHAIN),
      qb.and(PaymentDao.Properties.Type.eq(PaymentType.BTC_LN), PaymentDao.Properties.Status.notEq(PaymentStatus.INIT), PaymentDao.Properties.Direction.eq(PaymentDirection.RECEIVED)),
      qb.and(PaymentDao.Properties.Type.eq(PaymentType.BTC_LN), PaymentDao.Properties.Direction.eq(PaymentDirection.SENT)));
    qb.orderDesc(PaymentDao.Properties.Updated).limit(150);
    final List<Payment> list = qb.list();

    getActivity().runOnUiThread(() -> {
      if (mEmptyLabel != null) {
        mEmptyLabel.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
      }
    });

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
    if (getActivity() != null && getContext() != null) {
      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
      final CoinUnit prefUnit = WalletUtils.getPreferredCoinUnit(prefs);
      final String fiatCode = WalletUtils.getPreferredFiat(prefs);
      final boolean displayBalanceAsFiat = WalletUtils.shouldDisplayInFiat(prefs);
      new Thread() {
        @Override
        public void run() {
          final List<Payment> payments = getPayments();
          getActivity().runOnUiThread(() -> mPaymentAdapter.update(payments, fiatCode, prefUnit, displayBalanceAsFiat));
        }
      }.start();
    }
  }

  public void setSunsetNoticeClick(View.OnClickListener onNoticeClick) {
    this.onNoticeClick = onNoticeClick;
  }
}

