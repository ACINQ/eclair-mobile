package fr.acinq.eclair.swordfish.activity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
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

import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.payment.PaymentRequest;
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
import fr.acinq.eclair.swordfish.utils.CoinUtils;
import fr.acinq.eclair.swordfish.utils.Validators;

public class HomeActivity extends AppCompatActivity {

  private static final String TAG = "Home Activity";
  private ViewPager mViewPager;
  private HomePagerAdapter mPagerAdapter;
  private PaymentsListFragment mPaymentsListFragment;
  private ChannelsListFragment mChannelsListFragment;
  private ViewGroup mInvoiceButtonsView;
  private FloatingActionButton mScanInvoiceButton;
  private FloatingActionButton mPasteInvoiceButton;
  private ViewGroup mOpenChannelsButtonsView;
  private FloatingActionButton mScanURIButton;
  private FloatingActionButton mPasteURIButton;
  private TextView mPendingBalanceView;
  private AppBarLayout mAppBar;
  private ValueAnimator mFlashInBalanceAnimation;
  private ValueAnimator mPopInPaymentAnimation;
  private ValueAnimator mPopOutPaymentAnimation;
  private ViewGroup mPaymentPopin;
  private TextView mPaymentPopinText;
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
    mInvoiceButtonsView = (ViewGroup) findViewById(R.id.home_invoice_buttons);
    mScanInvoiceButton = (FloatingActionButton) findViewById(R.id.home_scan_invoice);
    mPasteInvoiceButton = (FloatingActionButton) findViewById(R.id.home_paste_invoice);
    mScanInvoiceButton.setOnLongClickListener(new View.OnLongClickListener() {
      @Override
      public boolean onLongClick(View v) {
        boolean isVisible = mPasteInvoiceButton.getVisibility() == View.VISIBLE;
        mPasteInvoiceButton.setVisibility(isVisible ? View.GONE : View.VISIBLE);
        mScanInvoiceButton.setBackgroundTintList(isVisible ? ColorStateList.valueOf(getColor(R.color.colorPrimary)) : ColorStateList.valueOf(getColor(R.color.colorGrey_4)));
        return true;
      }
    });
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
    mPaymentPopin = (ViewGroup) findViewById(R.id.home_paymentpopin);
    mPaymentPopinText = (TextView) findViewById(R.id.home_paymentpopin_text);
    setupPaymentAnimation();

    mViewPager = (ViewPager) findViewById(R.id.home_viewpager);
    mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
      @Override
      public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
      }

      @Override
      public void onPageSelected(int position) {
        mOpenChannelsButtonsView.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        mInvoiceButtonsView.setVisibility(position == 1 && mAvailableBalanceView.getAmountSat().amount() > 0 ? View.VISIBLE : View.GONE);
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

    mAppBar = (AppBarLayout) findViewById(R.id.home_appbar);
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

  public void home_doPasteInvoice(View view) {
    try {
      // read content to check if the PR is valid
      PaymentRequest extract = PaymentRequest.read(readFromClipboard());
      Intent intent = new Intent(this, CreatePaymentActivity.class);
      intent.putExtra(CreatePaymentActivity.EXTRA_INVOICE, PaymentRequest.write(extract));
      startActivity(intent);
    } catch (Throwable t) {
      Toast.makeText(this, "Invalid Invoice", Toast.LENGTH_SHORT).show();
    }
  }

  public void home_doScanInvoice(View view) {
    Intent intent = new Intent(this, ScanActivity.class);
    intent.putExtra(ScanActivity.EXTRA_SCAN_TYPE, ScanActivity.TYPE_INVOICE);
    startActivity(intent);
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
    Toast.makeText(this, "Payment failed: " + event.cause, Toast.LENGTH_LONG).show();
    mPaymentsListFragment.updateList();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onMessageEvent(SWPaymentEvent event) {
    animateBalance();
    mPaymentPopinText.setText("Successfully sent " + CoinUtils.getMilliBtcAmountFromInvoice(event.paymentRequest, true));
    mPaymentsListFragment.updateList();
  }

  private void animateBalance() {
    mFlashInBalanceAnimation.cancel();
    mFlashInBalanceAnimation.start();
    mPopInPaymentAnimation.cancel();
    mPopInPaymentAnimation.start();
  }

  private void setupPaymentAnimation() {
    // payment pop animation
    mPopInPaymentAnimation = ObjectAnimator.ofFloat(mPaymentPopin, "translationY", -80f, 0);
    mPopOutPaymentAnimation = ObjectAnimator.ofFloat(mPaymentPopin, "translationY", 0, -280f);
    mPopInPaymentAnimation.setDuration(300);
    mPopOutPaymentAnimation.setDuration(800);
    mPopOutPaymentAnimation.setStartDelay(1000);
    mPopInPaymentAnimation.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationStart(Animator animation) {
        mPaymentPopin.setVisibility(View.VISIBLE);
      }

      @Override
      public void onAnimationEnd(Animator animation) {
        mPopOutPaymentAnimation.start();
      }
    });
    mPopOutPaymentAnimation.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        mPaymentPopin.setVisibility(View.GONE);
      }
    });

    // balance flash animations
    final ValueAnimator.AnimatorUpdateListener flashUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator animator) {
        mAppBar.setBackgroundColor((int) animator.getAnimatedValue());
      }
    };
    mFlashInBalanceAnimation = ValueAnimator.ofArgb(getColor(R.color.bluegreen), getColor(R.color.colorPrimary));
    mFlashInBalanceAnimation.addUpdateListener(flashUpdateListener);
    mFlashInBalanceAnimation.setDuration(2000);
    mFlashInBalanceAnimation.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationStart(Animator animation) {
        mAppBar.setBackgroundColor(getColor(R.color.bluegreen));
      }
    });
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onMessageEvent(ChannelUpdateEvent event) {
    mChannelsListFragment.updateList();
  }

  private boolean hasEnoughChannnels() {
    if (EclairEventService.getChannelsMap().size() == 0) {
      findViewById(R.id.home_nochannels).setVisibility(View.VISIBLE);
      mPendingBalanceView.setVisibility(View.GONE);
      mInvoiceButtonsView.setVisibility(View.GONE);
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

    mAvailableBalanceView.setAmountSat(new Satoshi(event.availableBalanceSat));
    mInvoiceButtonsView.setVisibility(event.availableBalanceSat > 0 ? View.VISIBLE : View.GONE);

    // 2 - unavailable balance
    if (event.pendingBalanceSat > 0) {
      mPendingBalanceView.setText("+" + CoinUtils.getMilliBTCFormat().format(package$.MODULE$.satoshi2millibtc(new Satoshi(event.pendingBalanceSat)).amount()) + " mBTC pending");
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
