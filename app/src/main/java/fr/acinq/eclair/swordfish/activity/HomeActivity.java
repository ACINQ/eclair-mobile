package fr.acinq.eclair.swordfish.activity;

import android.content.Intent;
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
import android.widget.TextView;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.greenrobot.eventbus.util.ThrowableFailureEvent;

import java.util.ArrayList;
import java.util.List;

import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.swordfish.EclairEventService;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.customviews.CoinAmountView;
import fr.acinq.eclair.swordfish.events.BalanceUpdateEvent;
import fr.acinq.eclair.swordfish.events.ChannelUpdateEvent;
import fr.acinq.eclair.swordfish.events.SWPaymentEvent;
import fr.acinq.eclair.swordfish.fragment.ChannelsListFragment;
import fr.acinq.eclair.swordfish.fragment.PaymentsListFragment;
import fr.acinq.eclair.swordfish.utils.CoinFormat;

public class HomeActivity extends AppCompatActivity {

  private static final String TAG = "Home Activity";
  private ViewPager mViewPager;
  private HomePagerAdapter mPagerAdapter;
  private PaymentsListFragment mPaymentsListFragment;
  private ChannelsListFragment mChannelsListFragment;
  private FloatingActionButton mAddInvoiceButton;
  private FloatingActionButton mOpenChannelButton;
  private TextView mPendingBalanceView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_home);

    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(false);

    mAddInvoiceButton = (FloatingActionButton) findViewById(R.id.home_addinvoice);
    mOpenChannelButton = (FloatingActionButton) findViewById(R.id.home_openchannel);
    mPendingBalanceView = (TextView) findViewById(R.id.home_pendingbalance_value);

    mViewPager = (ViewPager) findViewById(R.id.home_viewpager);
    mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
      @Override
      public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
      }

      @Override
      public void onPageSelected(int position) {
        mOpenChannelButton.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        mAddInvoiceButton.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
      }

      @Override
      public void onPageScrollStateChanged(int state) {
      }
    });
    final List<Fragment> fragments = new ArrayList<>();
    mChannelsListFragment = new ChannelsListFragment();
    mPaymentsListFragment = new PaymentsListFragment();
    fragments.add(mChannelsListFragment);
    fragments.add(mPaymentsListFragment);
    mPagerAdapter = new HomePagerAdapter(getSupportFragmentManager(), fragments);
    mViewPager.setAdapter(mPagerAdapter);
    mViewPager.setCurrentItem(1);
  }

  @Override
  public void onStart() {
    EventBus.getDefault().register(this);
    super.onStart();

    updateBalance(EclairEventService.aggregateBalanceForEvent());
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

  public void home_addInvoice(View view) {
    Intent intent = new Intent(this, InvoiceInputActivity.class);
    startActivity(intent);
  }

  public void home_openChannel(View view) {
    Intent intent = new Intent(this, ChannelInputActivity.class);
    startActivity(intent);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onMessageEvent(BalanceUpdateEvent event) {
    updateBalance(event);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleFailureEvent(ThrowableFailureEvent event) {
    Toast.makeText(this, "Payment failed: " + event.getThrowable().getMessage(), Toast.LENGTH_LONG).show();
    mPaymentsListFragment.updateList();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onMessageEvent(SWPaymentEvent event) {
    mPaymentsListFragment.updateList();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onMessageEvent(ChannelUpdateEvent event) {
    mChannelsListFragment.updateList();
  }

  private boolean hasEnoughChannnels() {
    if (EclairEventService.getChannelsMap().size() == 0) {
      findViewById(R.id.home_nochannels).setVisibility(View.VISIBLE);
      mPendingBalanceView.setVisibility(View.GONE);
      mAddInvoiceButton.setVisibility(View.GONE);
      return false;
    }
    findViewById(R.id.home_nochannels).setVisibility(View.GONE);
    return true;
  }

  private void updateBalance(BalanceUpdateEvent event) {
    if (!hasEnoughChannnels()) {
      return;
    }

    // 1 - available balance
    findViewById(R.id.home_nochannels).setVisibility(View.GONE);
    CoinAmountView availableBalanceView = (CoinAmountView) findViewById(R.id.home_value_availablebalance);
    availableBalanceView.setAmountSat(new Satoshi(event.availableBalanceSat));
    mAddInvoiceButton.setVisibility(event.availableBalanceSat > 0 ? View.VISIBLE : View.GONE);

    // 2 - unavailable balance
    if (event.pendingBalanceSat > 0) {
      mPendingBalanceView.setText("+" + CoinFormat.getMilliBTCFormat().format(package$.MODULE$.satoshi2millibtc(new Satoshi(event.pendingBalanceSat)).amount()) + " mBTC pending");
      mPendingBalanceView.setVisibility(View.VISIBLE);
    } else {
      mPendingBalanceView.setVisibility(View.GONE);
    }

    // 3 - update total offchain balance
    CoinAmountView offchainBalanceView = (CoinAmountView) findViewById(R.id.home_offchain_balance_value);
    offchainBalanceView.setAmountSat(new Satoshi(event.availableBalanceSat + event.pendingBalanceSat + event.offlineBalanceSat));
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
