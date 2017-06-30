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
import fr.acinq.eclair.swordfish.events.BalanceUpdateEvent;
import fr.acinq.eclair.swordfish.events.ChannelUpdateEvent;
import fr.acinq.eclair.swordfish.events.SWPaymenFailedEvent;
import fr.acinq.eclair.swordfish.events.SWPaymentEvent;
import fr.acinq.eclair.swordfish.fragment.ChannelsListFragment;
import fr.acinq.eclair.swordfish.fragment.PaymentsListFragment;
import fr.acinq.eclair.swordfish.fragment.ReceivePaymentFragment;
import fr.acinq.eclair.swordfish.utils.Validators;

public class HomeActivity extends AppCompatActivity {

  private static final String TAG = "Home Activity";
  private ViewPager mViewPager;
  private HomePagerAdapter mPagerAdapter;
  private PaymentsListFragment mPaymentsListFragment;
  private ChannelsListFragment mChannelsListFragment;
  private ReceivePaymentFragment mReceivePaymentFragment;

  private ViewGroup mSendButtonsView;
  private ViewGroup mSendButtonsToggleView;
  private FloatingActionButton mSendButton;

  private ViewGroup mOpenChannelsButtonsView;
  private ViewGroup mOpenChannelButtonsToggleView;
  private FloatingActionButton mOpenChannelButton;

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

    mSendButtonsView = (ViewGroup) findViewById(R.id.home_send_buttons);
    mSendButtonsToggleView = (ViewGroup) findViewById(R.id.home_send_buttons_toggle);
    mSendButton = (FloatingActionButton) findViewById(R.id.home_send_button);

    mOpenChannelsButtonsView = (ViewGroup) findViewById(R.id.home_openchannel_buttons);
    mOpenChannelButtonsToggleView = (ViewGroup) findViewById(R.id.home_openchannel_buttons_toggle);
    mOpenChannelButton = (FloatingActionButton) findViewById(R.id.home_openchannel_button);

    mViewPager = (ViewPager) findViewById(R.id.home_viewpager);
    mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
      @Override
      public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
      }

      @Override
      public void onPageSelected(int position) {
        mOpenChannelsButtonsView.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
        mSendButtonsView.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
      }

      @Override
      public void onPageScrollStateChanged(int state) {
      }
    });

    final List<Fragment> fragments = new ArrayList<>();
    mReceivePaymentFragment = new ReceivePaymentFragment();
    fragments.add(mReceivePaymentFragment);
    mPaymentsListFragment = new PaymentsListFragment();
    fragments.add(mPaymentsListFragment);
    mChannelsListFragment = new ChannelsListFragment();
    fragments.add(mChannelsListFragment);
    mPagerAdapter = new HomePagerAdapter(getSupportFragmentManager(), fragments);
    mViewPager.setAdapter(mPagerAdapter);
    if (savedInstanceState != null && savedInstanceState.containsKey("currentPage")) {
      mViewPager.setCurrentItem(savedInstanceState.getInt("currentPage"));
    } else {
      mViewPager.setCurrentItem(1);
    }
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
  public void onPause() {
    super.onPause();
    home_closeSendButtons();
    home_closeOpenChannelButtons();
  }

  @Override
  public void onStop() {
    EventBus.getDefault().unregister(this);
    super.onStop();
  }

  @Override
  protected void onSaveInstanceState(Bundle bundle) {
    super.onSaveInstanceState(bundle);
    bundle.putInt("currentPage", mViewPager.getCurrentItem());
  }

  @Override
  protected void onRestoreInstanceState (Bundle savedInstanceState) {
    mViewPager.setCurrentItem(savedInstanceState.getInt("currentPage"));
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

  public void home_toggleSendButtons(View view) {
    boolean isVisible = mSendButtonsToggleView.getVisibility() == View.VISIBLE;
    mSendButton.animate().rotation(isVisible ? 0 : -90).setDuration(150).start();
    mSendButton.setBackgroundTintList(ColorStateList.valueOf(getColor(isVisible ? R.color.colorPrimary : R.color.colorGrey_4)));
    mSendButtonsToggleView.setVisibility(isVisible ? View.GONE : View.VISIBLE);
  }
  public void home_closeSendButtons() {
    mSendButton.animate().rotation(0).setDuration(150).start();
    mSendButton.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.colorPrimary)));
    mSendButtonsToggleView.setVisibility(View.GONE);
  }
  public void home_toggleOpenChannelButtons(View view) {
    boolean isVisible = mOpenChannelButtonsToggleView.getVisibility() == View.VISIBLE;
    mOpenChannelButton.animate().rotation(isVisible ? 0 : 135).setDuration(150).start();
    mOpenChannelButton.setBackgroundTintList(ColorStateList.valueOf(getColor(isVisible ? R.color.green : R.color.colorGrey_4)));
    mOpenChannelButtonsToggleView.setVisibility(isVisible ? View.GONE : View.VISIBLE);
  }

  public void home_closeOpenChannelButtons() {
    mOpenChannelButton.animate().rotation(0).setDuration(150).start();
    mOpenChannelButton.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.green)));
    mOpenChannelButtonsToggleView.setVisibility(View.GONE);
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

  private void updateBalance(BalanceUpdateEvent event) {
    if (EclairEventService.getChannelsMap().size() == 0) {
      return;
    }
    mAvailableBalanceView.setAmountMsat(new MilliSatoshi(event.availableBalanceMsat + event.offlineBalanceMsat + event.pendingBalanceMsat));
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
