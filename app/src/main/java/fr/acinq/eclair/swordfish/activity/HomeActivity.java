package fr.acinq.eclair.swordfish.activity;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.swordfish.EclairEventService;
import fr.acinq.eclair.swordfish.EclairHelper;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.customviews.CoinAmountView;
import fr.acinq.eclair.swordfish.customviews.FabText;
import fr.acinq.eclair.swordfish.events.BalanceUpdateEvent;
import fr.acinq.eclair.swordfish.events.ChannelUpdateEvent;
import fr.acinq.eclair.swordfish.events.SWPaymenFailedEvent;
import fr.acinq.eclair.swordfish.events.SWPaymentEvent;
import fr.acinq.eclair.swordfish.fragment.ChannelsListFragment;
import fr.acinq.eclair.swordfish.fragment.PaymentsListFragment;
import fr.acinq.eclair.swordfish.utils.CoinUtils;
import fr.acinq.eclair.swordfish.utils.Validators;

public class HomeActivity extends AppCompatActivity {

  private static final String TAG = "Home Activity";
  private ViewPager mViewPager;
  private HomePagerAdapter mPagerAdapter;
  private PaymentsListFragment mPaymentsListFragment;
  private ChannelsListFragment mChannelsListFragment;

  private ViewGroup mMainButtonsView;
  private ViewGroup mMainButtonsToggleView;
  private FloatingActionButton mMainButton;
  private FabText mSendScanButton;
  private FabText mSendPasteButton;
  private FabText mReceiveButton;

  private ViewGroup mOpenChannelsButtonsView;
  private FloatingActionButton mScanURIButton;
  private FloatingActionButton mPasteURIButton;

  private TextView mPendingBalanceView;
  private CoinAmountView mAvailableBalanceView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_home);

    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(false);

    mAvailableBalanceView = (CoinAmountView) findViewById(R.id.home_value_availablebalance);

    mMainButtonsView = (ViewGroup) findViewById(R.id.home_main_buttons);
    mMainButtonsToggleView = (ViewGroup) findViewById(R.id.home_main_buttons_toggle);
    mMainButton = (FloatingActionButton) findViewById(R.id.home_mainaction);
    mSendScanButton = (FabText) findViewById(R.id.home_send_scan);
    mSendPasteButton = (FabText) findViewById(R.id.home_send_paste);
    mReceiveButton = (FabText) findViewById(R.id.home_receive);

    mOpenChannelsButtonsView = (ViewGroup) findViewById(R.id.home_openchannel_buttons);
    mScanURIButton = (FloatingActionButton) findViewById(R.id.home_scan_uri);
    mPasteURIButton = (FloatingActionButton) findViewById(R.id.home_paste_uri);
    mScanURIButton.setOnLongClickListener(new View.OnLongClickListener() {
      @Override
      public boolean onLongClick(View v) {
        boolean isVisible = mPasteURIButton.getVisibility() == View.VISIBLE;
        mPasteURIButton.setVisibility(isVisible ? View.GONE : View.VISIBLE);
        mScanURIButton.setBackgroundTintList(isVisible ? ColorStateList.valueOf(getColor(R.color.green)) : ColorStateList.valueOf(getColor(R.color.colorGrey_4)));
        return true;
      }
    });
    mPendingBalanceView = (TextView) findViewById(R.id.home_pendingbalance_value);

    mViewPager = (ViewPager) findViewById(R.id.home_viewpager);
    mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
      @Override
      public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
      }

      @Override
      public void onPageSelected(int position) {
        mOpenChannelsButtonsView.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        mMainButtonsView.setVisibility(position == 0 && mAvailableBalanceView.getAmountMsat().amount() > 0 ? View.VISIBLE : View.GONE);
      }

      @Override
      public void onPageScrollStateChanged(int state) {
      }
    });

    final List<Fragment> fragments = new ArrayList<>();
    mPaymentsListFragment = new PaymentsListFragment();
    fragments.add(mPaymentsListFragment);
    mChannelsListFragment = new ChannelsListFragment();
    fragments.add(mChannelsListFragment);
    mPagerAdapter = new HomePagerAdapter(getSupportFragmentManager(), fragments);
    mViewPager.setAdapter(mPagerAdapter);
    mViewPager.setCurrentItem(0);

  }

  @Override
  public void onStart() {
    super.onStart();
    if (!EclairHelper.hasInstance()) {
      startActivity(new Intent(this, LauncherActivity.class));
      finish();
    }
  }

  @Override
  public void onResume() {
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this);
    }
    super.onResume();
    updateBalance(EclairEventService.aggregateBalanceForEvent());
  }

  @Override
  public void onStop() {
    EventBus.getDefault().unregister(this);
    super.onStop();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_home, menu);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.menu_home_settings:
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        startActivity(settingsIntent);
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  private String readFromClipboard() {
    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemAt(0) != null && clipboard.getPrimaryClip().getItemAt(0).getText() != null) {
      return clipboard.getPrimaryClip().getItemAt(0).getText().toString();
    }
    return "";
  }

  public void home_sendPaste(View view) {
    Intent intent = new Intent(this, CreatePaymentActivity.class);
    intent.putExtra(CreatePaymentActivity.EXTRA_INVOICE, readFromClipboard());
    startActivity(intent);
  }

  public void home_sendScan(View view) {
    Intent intent = new Intent(this, ScanActivity.class);
    intent.putExtra(ScanActivity.EXTRA_SCAN_TYPE, ScanActivity.TYPE_INVOICE);
    startActivity(intent);
  }

  public void home_toggleMain(View view) {
    boolean isVisible = mMainButtonsToggleView.getVisibility() == View.VISIBLE;
    mMainButton.animate().rotation(isVisible ? 0 : 135).setDuration(150).start();
    mMainButton.setBackgroundTintList(ColorStateList.valueOf(getColor(isVisible ? R.color.colorPrimary : R.color.colorGrey_4)));
    mMainButtonsToggleView.setVisibility(isVisible ? View.GONE : View.VISIBLE);
  }

  public void home_receive(View view) {
  }

  public void home_doPasteURI(View view) {
    String uri = readFromClipboard();
    if (Validators.HOST_REGEX.matcher(uri).matches()) {
      Intent intent = new Intent(getBaseContext(), OpenChannelActivity.class);
      intent.putExtra(OpenChannelActivity.EXTRA_NEW_HOST_URI, uri);
      startActivity(intent);
    } else {
      Toast.makeText(this, "Invalid Lightning Node URI", Toast.LENGTH_SHORT).show();
    }
  }

  public void home_doScanURI(View view) {
    Intent intent = new Intent(this, ScanActivity.class);
    intent.putExtra(ScanActivity.EXTRA_SCAN_TYPE, ScanActivity.TYPE_URI);
    startActivity(intent);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onMessageEvent(BalanceUpdateEvent event) {
    updateBalance(event);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleFailureEvent(SWPaymenFailedEvent event) {

    Intent intent = new Intent(this, PaymentFailureActivity.class);
    intent.putExtra(PaymentFailureActivity.EXTRA_PAYMENTFAILURE_DESC, event.payment.description);
    intent.putExtra(PaymentFailureActivity.EXTRA_PAYMENTFAILURE_AMOUNT, event.payment.amountPaid);
    intent.putExtra(PaymentFailureActivity.EXTRA_PAYMENTFAILURE_CAUSE, event.cause);
    startActivity(intent);
    mPaymentsListFragment.updateList();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onMessageEvent(SWPaymentEvent event) {

    Intent intent = new Intent(this, PaymentSuccessActivity.class);
    intent.putExtra(PaymentSuccessActivity.EXTRA_PAYMENTSUCCESS_DESC, event.payment.description);
    intent.putExtra(PaymentSuccessActivity.EXTRA_PAYMENTSUCCESS_AMOUNT, (event.payment.amountPaid));
    startActivity(intent);

    mPaymentsListFragment.updateList();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onMessageEvent(ChannelUpdateEvent event) {
    mChannelsListFragment.updateList();
  }

  private boolean hasEnoughChannnels() {
    if (EclairEventService.getChannelsMap().size() == 0) {
      mPendingBalanceView.setVisibility(View.GONE);
      mMainButtonsView.setVisibility(View.GONE);
      return false;
    }
    return true;
  }

  private void updateBalance(BalanceUpdateEvent event) {
    if (!hasEnoughChannnels()) {
      return;
    }

    // 1 - available balance
    mAvailableBalanceView.setAmountMsat(new MilliSatoshi(event.availableBalanceMsat));
    mMainButtonsView.setVisibility(event.availableBalanceMsat > 0 ? View.VISIBLE : View.GONE);

    // 2 - unavailable balance
    if (event.pendingBalanceMsat > 0) {
      mPendingBalanceView.setText("+" + CoinUtils.formatAmountMilliBtc(new MilliSatoshi(event.pendingBalanceMsat)) + " mBTC pending");
      mPendingBalanceView.setVisibility(View.VISIBLE);
    } else {
      mPendingBalanceView.setVisibility(View.GONE);
    }

    // 3 - update total offchain balance
    CoinAmountView offchainBalanceView = (CoinAmountView) findViewById(R.id.home_offchain_balance_value);
    offchainBalanceView.setAmountMsat(new MilliSatoshi(event.availableBalanceMsat + event.pendingBalanceMsat + event.offlineBalanceMsat));
  }

  private class HomePagerAdapter extends FragmentStatePagerAdapter {
    private final List<Fragment> mFragmentList;

    public HomePagerAdapter(FragmentManager fm, List<Fragment> fragments) {
      super(fm);
      mFragmentList = fragments;
    }

    @Override
    public Fragment getItem(int position) {
      return mFragmentList.get(position);
    }

    @Override
    public int getCount() {
      return mFragmentList.size();
    }
  }

}
