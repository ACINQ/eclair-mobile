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

package fr.acinq.eclair.wallet.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewStub;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.greenrobot.eventbus.util.ThrowableFailureEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.blockchain.electrum.ElectrumClient;
import fr.acinq.eclair.blockchain.electrum.ElectrumWallet;
import fr.acinq.eclair.router.SyncProgress;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.actors.NodeSupervisor;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityHomeBinding;
import fr.acinq.eclair.wallet.events.BalanceUpdateEvent;
import fr.acinq.eclair.wallet.events.BitcoinPaymentFailedEvent;
import fr.acinq.eclair.wallet.events.ChannelUpdateEvent;
import fr.acinq.eclair.wallet.events.LNPaymentFailedEvent;
import fr.acinq.eclair.wallet.events.LNPaymentSuccessEvent;
import fr.acinq.eclair.wallet.events.PaymentEvent;
import fr.acinq.eclair.wallet.fragments.ChannelsListFragment;
import fr.acinq.eclair.wallet.fragments.PaymentsListFragment;
import fr.acinq.eclair.wallet.fragments.ReceivePaymentFragment;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;

import static fr.acinq.eclair.wallet.adapters.LocalChannelItemHolder.EXTRA_CHANNEL_ID;

public class HomeActivity extends EclairActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

  private final Logger log = LoggerFactory.getLogger(HomeActivity.class);

  public static final String EXTRA_PAGE = BuildConfig.APPLICATION_ID + "EXTRA_PAGE";
  public static final String EXTRA_PAYMENT_URI = BuildConfig.APPLICATION_ID + "EXTRA_PAYMENT_URI";

  private ActivityHomeBinding mBinding;

  private ViewStub mStubIntro;
  private int introStep = 0;
  private boolean canSendPayments = false;

  private PaymentsListFragment mPaymentsListFragment;
  private ChannelsListFragment mChannelsListFragment;
  private Handler mExchangeRateHandler = new Handler();
  private Runnable mExchangeRateRunnable;
  // debounce for requesting payment list update -- max once per 2 secs
  private final Animation mBlinkingAnimation = new AlphaAnimation(0.3f, 1);
  private final Animation mRotatingAnimation = new RotateAnimation(0, -360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
    0.5f);

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_home);

    setSupportActionBar(mBinding.toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(false);
    ab.setDisplayShowTitleEnabled(false);

    if (!checkInit()) {
      finish();
      return;
    }

    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    // --- check initial app state
    if (prefs.getBoolean(Constants.SETTING_SHOW_INTRO, true)) {
      mStubIntro = findViewById(R.id.home_stub_intro);
      displayIntro(prefs);
    }
    // setup content
    if (app.isWalletConnected()) {
      enableSendPaymentButtons();
    } else {
      disableSendPaymentButtons();
    }

    setUpTabs(savedInstanceState);
    setUpBalanceInteraction(prefs);
    setUpExchangeRate();

    // --- animations
    mBlinkingAnimation.setDuration(500);
    mBlinkingAnimation.setRepeatCount(Animation.INFINITE);
    mBlinkingAnimation.setRepeatMode(Animation.REVERSE);

    mRotatingAnimation.setDuration(2000);
    mRotatingAnimation.setRepeatCount(Animation.INFINITE);
    mRotatingAnimation.setInterpolator(new LinearInterpolator());
    mBinding.syncProgressIcon.startAnimation(mRotatingAnimation);

    final Intent intent = getIntent();
    if (intent.hasExtra(StartupActivity.ORIGIN)) {
      final String origin = intent.getStringExtra(StartupActivity.ORIGIN);
      final String originParam = intent.getStringExtra(StartupActivity.ORIGIN_EXTRA);
      if (ChannelDetailsActivity.class.getSimpleName().equals(origin)) {
        final Intent channelDetailsIntent = new Intent(this, ChannelDetailsActivity.class);
        channelDetailsIntent.putExtra(EXTRA_CHANNEL_ID, originParam);
        startActivity(channelDetailsIntent);
      }
    } else {
      // app may be started with a payment request intent
      readURIIntent(getIntent());
    }
  }

  private void setUpTabs(final Bundle savedInstanceState) {
    mBinding.viewpager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
      @Override
      public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
      }

      @Override
      public void onPageSelected(int position) {
        mBinding.openchannelButtons.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
        mBinding.sendpaymentButtons.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
      }

      @Override
      public void onPageScrollStateChanged(int state) {
      }
    });

    final List<Fragment> fragments = new ArrayList<>();
    fragments.add(new ReceivePaymentFragment());
    mPaymentsListFragment = new PaymentsListFragment();
    fragments.add(mPaymentsListFragment);
    mChannelsListFragment = new ChannelsListFragment();
    fragments.add(mChannelsListFragment);
    HomePagerAdapter mPagerAdapter = new HomePagerAdapter(getSupportFragmentManager(), fragments);

    mBinding.viewpager.setAdapter(mPagerAdapter);
    mBinding.tabs.setupWithViewPager(mBinding.viewpager);

    if (savedInstanceState != null && savedInstanceState.containsKey("currentPage")) {
      mBinding.viewpager.setCurrentItem(savedInstanceState.getInt("currentPage"));
    } else {
      Intent intent = getIntent();
      mBinding.viewpager.setCurrentItem(intent.getIntExtra(EXTRA_PAGE, 1));
    }
  }

  private void setUpBalanceInteraction(final SharedPreferences prefs) {
    mBinding.balance.setOnClickListener(view -> {
      boolean displayBalanceAsFiat = WalletUtils.shouldDisplayInFiat(prefs);
      prefs.edit().putBoolean(Constants.SETTING_DISPLAY_IN_FIAT, !displayBalanceAsFiat).commit();
      mBinding.balanceOnchain.refreshUnits();
      mBinding.balanceLightning.refreshUnits();
      mBinding.balanceTotal.refreshUnits();
      mPaymentsListFragment.refreshList();
      mChannelsListFragment.updateActiveChannelsList();
    });
  }

  private void setUpExchangeRate() {
    final RequestQueue queue = Volley.newRequestQueue(this);
    final JsonObjectRequest request = WalletUtils.exchangeRateRequest(PreferenceManager.getDefaultSharedPreferences(getBaseContext()));
    mExchangeRateRunnable = new Runnable() {
      @Override
      public void run() {
        queue.add(request);
        mExchangeRateHandler.postDelayed(this, 20 * 60 * 1000);
      }
    };
  }

  @Override
  public void onStart() {
    super.onStart();
    mBinding.balanceTotal.refreshUnits();
    mBinding.balanceOnchain.refreshUnits();
    mBinding.balanceLightning.refreshUnits();
    refreshChannelsBackupWarning(PreferenceManager.getDefaultSharedPreferences(this));
  }

  @Override
  public void onResume() {
    super.onResume();
    if (checkInit()) {
      if (!EventBus.getDefault().isRegistered(this)) {
        EventBus.getDefault().register(this);
      }
      PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
      // refresh exchange rate
      mExchangeRateHandler.post(mExchangeRateRunnable);
      // refresh LN balance
      updateBalance();
    }
  }

  @Override
  protected void onNewIntent(final Intent intent) {
    super.onNewIntent(intent);
    readURIIntent(intent);
  }

  private void readURIIntent(final Intent intent) {
    final Uri paymentRequest = intent.getParcelableExtra(EXTRA_PAYMENT_URI);
    if (paymentRequest != null) {
      switch (paymentRequest.getScheme()) {
        case "bitcoin":
        case "lightning":
          final Intent paymentIntent = new Intent(this, SendPaymentActivity.class);
          paymentIntent.putExtra(SendPaymentActivity.EXTRA_INVOICE, paymentRequest.toString());
          startActivity(paymentIntent);
          break;
        default:
          log.error("unhandled payment scheme {}", paymentRequest);
      }
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
    mExchangeRateHandler.removeCallbacks(mExchangeRateRunnable);
    closeSendPaymentButtons();
    closeOpenChannelButtons();
  }

  @Override
  public void onStop() {
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    EventBus.getDefault().unregister(this);
    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle bundle) {
    bundle.putInt("currentPage", mBinding.viewpager.getCurrentItem());
    super.onSaveInstanceState(bundle);
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    mBinding.viewpager.setCurrentItem(savedInstanceState.getInt("currentPage"));
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_home, menu);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.menu_home_networkinfos:
        startActivity(new Intent(this, NetworkInfosActivity.class));
        return true;
      case R.id.menu_home_about:
        startActivity(new Intent(this, AboutActivity.class));
        return true;
      case R.id.menu_home_faq:
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ACINQ/eclair-wallet/wiki/FAQ")));
        return true;
      case R.id.menu_home_preferences:
        startActivity(new Intent(this, PreferencesActivity.class));
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
    if (Constants.SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_ENABLED.equals(key)) {
      refreshChannelsBackupWarning(prefs);
    } else if (Constants.SETTING_BTC_UNIT.equals(key) || Constants.SETTING_SELECTED_FIAT_CURRENCY.equals(key)) {
      mBinding.balanceTotal.refreshUnits();
      mBinding.balanceOnchain.refreshUnits();
      mBinding.balanceLightning.refreshUnits();
    }
  }

  private void refreshChannelsBackupWarning(SharedPreferences prefs) {
    final boolean isBackupEnabled = prefs.getBoolean(Constants.SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_ENABLED, false);
    if (!isBackupEnabled) {
      mBinding.channelsBackupWarning.startAnimation(mBlinkingAnimation);
    } else {
      mBinding.channelsBackupWarning.clearAnimation();
    }
    mBinding.setChannelsBackupEnabled(isBackupEnabled);
  }

  private void displayIntro(final SharedPreferences prefs) {
    final View inflatedIntro = mStubIntro.inflate();
    final View introWelcome = findViewById(R.id.home_intro_welcome);
    final View introReceive = findViewById(R.id.home_intro_receive);
    final View introOpenChannel = findViewById(R.id.home_intro_openchannel);
    final View introOpenChannelPatience = findViewById(R.id.home_intro_openchannel_patience);
    final View introSendPayment = findViewById(R.id.home_intro_sendpayment);
    introWelcome.setVisibility(View.VISIBLE);
    inflatedIntro.setOnClickListener(v -> {
      introStep++;
      if (introStep > 4) {
        mStubIntro.setVisibility(View.GONE);
        prefs.edit().putBoolean(Constants.SETTING_SHOW_INTRO, false).apply();
      } else {
        introWelcome.setVisibility(View.GONE);
        introReceive.setVisibility(introStep == 1 ? View.VISIBLE : View.GONE);
        introOpenChannel.setVisibility(introStep == 2 ? View.VISIBLE : View.GONE);
        introOpenChannelPatience.setVisibility(introStep == 3 ? View.VISIBLE : View.GONE);
        introSendPayment.setVisibility(introStep == 4 ? View.VISIBLE : View.GONE);
        if (introStep == 1) {
          mBinding.viewpager.setCurrentItem(0);
        } else if (introStep == 2 || introStep == 3) {
          mBinding.viewpager.setCurrentItem(2);
        } else {
          mBinding.viewpager.setCurrentItem(1);
        }
      }
    });
  }

  private String readFromClipboard() {
    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemAt(0) != null && clipboard.getPrimaryClip().getItemAt(0).getText() != null) {
      return clipboard.getPrimaryClip().getItemAt(0).getText().toString();
    }
    return "";
  }

  public void enableChannelsBackup(final View view) {
    startActivity(new Intent(this, ChannelsBackupSettingsActivity.class));
  }

  public void pasteSendPaymentRequest(View view) {
    if (canSendPayments) {
      Intent intent = new Intent(this, SendPaymentActivity.class);
      intent.putExtra(SendPaymentActivity.EXTRA_INVOICE, readFromClipboard());
      startActivity(intent);
    }
  }

  public void scanSendPaymentRequest(View view) {
    if (canSendPayments) {
      Intent intent = new Intent(this, ScanActivity.class);
      intent.putExtra(ScanActivity.EXTRA_SCAN_TYPE, ScanActivity.TYPE_INVOICE);
      startActivity(intent);
    }
  }

  public void toggleSendPaymentButtons(View view) {
    boolean isVisible = mBinding.sendpaymentActionsList.getVisibility() == View.VISIBLE;
    mBinding.sendpaymentToggler.animate().rotation(isVisible ? 0 : -90).setInterpolator(new AccelerateDecelerateInterpolator()).setDuration(150).start();
    mBinding.sendpaymentToggler.setBackgroundTintList(ContextCompat.getColorStateList(this, isVisible ? R.color.primary : R.color.grey_4));
    mBinding.sendpaymentActionsList.setVisibility(isVisible ? View.GONE : View.VISIBLE);
  }

  private void enableSendPaymentButtons() {
    canSendPayments = true;
    closeSendPaymentButtons();
    closeOpenChannelButtons();
    mBinding.sendpaymentToggler.setEnabled(true);
    mBinding.homeOpenchannelToggler.setEnabled(true);
    mBinding.sendpaymentToggler.setAlpha(1f);
    mBinding.homeOpenchannelToggler.setAlpha(1f);
  }

  private void disableSendPaymentButtons() {
    canSendPayments = false;
    closeSendPaymentButtons();
    closeOpenChannelButtons();
    mBinding.sendpaymentToggler.setEnabled(false);
    mBinding.homeOpenchannelToggler.setEnabled(false);
    mBinding.sendpaymentToggler.setAlpha(0.4f);
    mBinding.homeOpenchannelToggler.setAlpha(0.4f);
  }

  public void closeSendPaymentButtons() {
    mBinding.sendpaymentToggler.animate().rotation(0).setInterpolator(new AccelerateDecelerateInterpolator()).setDuration(150).start();
    mBinding.sendpaymentToggler.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary));
    mBinding.sendpaymentActionsList.setVisibility(View.GONE);
  }

  public void toggleOpenChannelButtons(View view) {
    boolean isVisible = mBinding.openchannelActionsList.getVisibility() == View.VISIBLE;
    mBinding.homeOpenchannelToggler.animate().rotation(isVisible ? 0 : 135).setInterpolator(new AccelerateDecelerateInterpolator()).setDuration(150).start();
    mBinding.homeOpenchannelToggler.setBackgroundTintList(ContextCompat.getColorStateList(this, isVisible ? R.color.green : R.color.grey_4));
    mBinding.openchannelActionsList.setVisibility(isVisible ? View.GONE : View.VISIBLE);
  }

  public void closeOpenChannelButtons() {
    mBinding.homeOpenchannelToggler.animate().rotation(0).setInterpolator(new AccelerateDecelerateInterpolator()).setDuration(150).start();
    mBinding.homeOpenchannelToggler.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.green));
    mBinding.openchannelActionsList.setVisibility(View.GONE);
  }

  public void pasteNodeURI(View view) {
    final String uri = readFromClipboard();
    final Intent intent = new Intent(getBaseContext(), OpenChannelActivity.class);
    intent.putExtra(OpenChannelActivity.EXTRA_NEW_HOST_URI, uri);
    startActivity(intent);
  }

  public void scanNodeURI(View view) {
    Intent intent = new Intent(this, ScanActivity.class);
    intent.putExtra(ScanActivity.EXTRA_SCAN_TYPE, ScanActivity.TYPE_URI);
    startActivity(intent);
  }

  public void openChannelWithAcinq(View view) {
    Intent intent = new Intent(getBaseContext(), OpenChannelActivity.class);
    intent.putExtra(OpenChannelActivity.EXTRA_NEW_HOST_URI, WalletUtils.ACINQ_NODE);
    startActivity(intent);
  }

  public void openChannelRandom(View view) {
    Intent intent = new Intent(getBaseContext(), OpenChannelActivity.class);
    intent.putExtra(OpenChannelActivity.EXTRA_USE_DNS_SEED, true);
    startActivity(intent);
  }

  public void copyReceptionAddress(View view) {
    try {
      ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
      clipboard.setPrimaryClip(ClipData.newPlainText("Bitcoin address", app.getWalletAddress()));
      Toast.makeText(this.getApplicationContext(), "Copied address to clipboard", Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      log.debug("failed to copy address with cause {}", e.getMessage());
      Toast.makeText(this.getApplicationContext(), "Could not copy address", Toast.LENGTH_SHORT).show();
    }
  }

  public void popinSyncProgress(final View view) {
    getCustomDialog(R.string.home_sync_progress_about).setPositiveButton(R.string.btn_ok, null).create().show();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleSyncProgressEvent(SyncProgress progress) {
    final int p = (int) (progress.progress() * 100);
    if (p >= 100) {
      mBinding.syncProgressIcon.clearAnimation();
    } else {
      if (mBinding.syncProgressIcon.getAnimation() == null || mBinding.syncProgressIcon.getAnimation().hasEnded()) {
        mBinding.syncProgressIcon.startAnimation(mRotatingAnimation);
      }
    }
    mBinding.setSyncProgress(p);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleBalanceEvent(BalanceUpdateEvent event) {
    updateBalance();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleElectrumStateEvent(ElectrumWallet.WalletReady event) {
    enableSendPaymentButtons();
    mBinding.homeConnectionStatus.setVisibility(View.GONE);

    final Long diffTimestamp = Math.abs(System.currentTimeMillis() / 1000L - event.timestamp());
    if (diffTimestamp < Constants.DESYNC_DIFF_TIMESTAMP_SEC) {
      mBinding.homeConnectionStatus.setVisibility(View.GONE);
    } else {
      mBinding.homeConnectionStatusTitle.setText(getString(R.string.chain_late));
      mBinding.homeConnectionStatusDesc.setText(getString(R.string.chain_late_desc));
      mBinding.homeConnectionStatus.setVisibility(View.VISIBLE);
    }
    updateBalance();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleLNPaymentFailedEvent(LNPaymentFailedEvent event) {
    Intent intent = new Intent(this, PaymentFailureActivity.class);
    intent.putExtra(PaymentFailureActivity.EXTRA_PAYMENT_HASH, event.paymentHash);
    intent.putExtra(PaymentFailureActivity.EXTRA_PAYMENT_DESC, event.paymentDescription);
    intent.putExtra(PaymentFailureActivity.EXTRA_PAYMENT_SIMPLE_ONLY, event.isSimple);
    intent.putExtra(PaymentFailureActivity.EXTRA_PAYMENT_SIMPLE_MESSAGE, event.simpleMessage);
    intent.putParcelableArrayListExtra(PaymentFailureActivity.EXTRA_PAYMENT_ERRORS, event.errors);
    startActivity(intent);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handlePaymentEvent(PaymentEvent event) {
    mPaymentsListFragment.updateList();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleLNPaymentSuccessEvent(LNPaymentSuccessEvent event) {
    Intent intent = new Intent(this, PaymentSuccessActivity.class);
    intent.putExtra(PaymentSuccessActivity.EXTRA_PAYMENTSUCCESS_DESC, event.payment.getDescription());
    intent.putExtra(PaymentSuccessActivity.EXTRA_PAYMENTSUCCESS_AMOUNT, event.payment.getAmountPaidMsat());
    startActivity(intent);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleBitcoinPaymentFailedEvent(BitcoinPaymentFailedEvent event) {
    Toast.makeText(getApplicationContext(), getString(R.string.payment_toast_failure, event.getMessage()), Toast.LENGTH_LONG).show();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleChannelUpdateEvent(ChannelUpdateEvent event) {
    mChannelsListFragment.updateActiveChannelsList();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleThrowableEvent(ThrowableFailureEvent event) {
    log.debug("event failed with cause {}", event.getThrowable().getMessage());
  }

  private void updateBalance() {
    final MilliSatoshi lightningBalance = NodeSupervisor.getChannelsBalance();
    final MilliSatoshi walletBalance = app == null ? new MilliSatoshi(0) : package$.MODULE$.satoshi2millisatoshi(app.getOnchainBalance());
    mBinding.balanceTotal.setAmountMsat(new MilliSatoshi(lightningBalance.amount() + walletBalance.amount()));
    mBinding.balanceOnchain.setAmountMsat(walletBalance);
    mBinding.balanceLightning.setAmountMsat(lightningBalance);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleDisconnectionEvent(ElectrumClient.ElectrumDisconnected$ event) {
    disableSendPaymentButtons();
    mBinding.homeConnectionStatusTitle.setText(getString(R.string.chain_disconnected));
    mBinding.homeConnectionStatusDesc.setText(getString(R.string.chain_disconnected_desc));
    mBinding.homeConnectionStatus.setVisibility(View.VISIBLE);
  }

  private class HomePagerAdapter extends FragmentStatePagerAdapter {
    private final List<Fragment> mFragmentList;
    private final String[] titles = new String[]{getString(R.string.receive_title), getString(R.string.payments_title), getString(R.string.localchannels_title)};

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

    @Override
    public CharSequence getPageTitle(int position) {
      return titles[position];
    }
  }

}
