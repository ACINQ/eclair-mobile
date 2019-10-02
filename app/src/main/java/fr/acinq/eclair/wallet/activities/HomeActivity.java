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

package fr.acinq.eclair.wallet.activities;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import com.google.android.material.tabs.TabLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import android.text.Html;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.view.*;
import android.view.animation.*;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.common.util.Strings;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.blockchain.electrum.ElectrumClient;
import fr.acinq.eclair.blockchain.electrum.ElectrumWallet;
import fr.acinq.eclair.router.SyncProgress;
import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.actors.NodeSupervisor;
import fr.acinq.eclair.wallet.databinding.ActivityHomeBinding;
import fr.acinq.eclair.wallet.events.*;
import fr.acinq.eclair.wallet.fragments.ChannelsListFragment;
import fr.acinq.eclair.wallet.fragments.PaymentsListFragment;
import fr.acinq.eclair.wallet.fragments.ReceivePaymentFragment;
import fr.acinq.eclair.wallet.utils.BackupHelper;
import fr.acinq.eclair.wallet.services.CheckElectrumWorker;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.TechnicalHelper;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.greenrobot.eventbus.util.ThrowableFailureEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static fr.acinq.eclair.wallet.adapters.LocalChannelItemHolder.EXTRA_CHANNEL_ID;

public class HomeActivity extends EclairActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

  private final Logger log = LoggerFactory.getLogger(HomeActivity.class);

  public static final String EXTRA_PAGE = BuildConfig.APPLICATION_ID + "EXTRA_PAGE";
  public static final String EXTRA_PAYMENT_URI = BuildConfig.APPLICATION_ID + "EXTRA_PAYMENT_URI";

  private ActivityHomeBinding mBinding;

  private ViewStub mStubIntro;
  private int introStep = 0;
  private boolean canSendPayments = false;

  private ReceivePaymentFragment mReceivePaymentFragment;
  private PaymentsListFragment mPaymentsListFragment;
  private ChannelsListFragment mChannelsListFragment;
  private final Animation mBlinkingAnimation = new AlphaAnimation(0.3f, 1);

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

    setUpTabs(savedInstanceState);
    setUpBalanceInteraction(prefs);

    // --- animations
    mBlinkingAnimation.setDuration(500);
    mBlinkingAnimation.setRepeatCount(Animation.INFINITE);
    mBlinkingAnimation.setRepeatMode(Animation.REVERSE);

    // show an 'update available' message if the last released version is 5 code higher than the current version on device
    // this message should not display more than once per day
    if (App.walletContext != null && App.walletContext.version - BuildConfig.VERSION_CODE >= 5
      && System.currentTimeMillis() - prefs.getLong(Constants.SETTING_LAST_UPDATE_WARNING_TIMESTAMP, 0) > DateUtils.DAY_IN_MILLIS) {
      new AlertDialog.Builder(HomeActivity.this, R.style.CustomDialog)
        .setTitle(R.string.startup_update_wallet_title)
        .setMessage(R.string.startup_update_wallet_message)
        .setPositiveButton(R.string.btn_ok, null)
        .show();
      prefs.edit().putLong(Constants.SETTING_LAST_UPDATE_WARNING_TIMESTAMP, System.currentTimeMillis()).apply();
    }

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

  private void checkBackgroundRunnableWarning() {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    if (prefs.getBoolean(Constants.SETTING_BACKGROUND_CANNOT_RUN_WARNING, false)) {
      // show warning -- only once
      final AlertDialog d = new AlertDialog.Builder(HomeActivity.this, R.style.CustomDialog)
        .setTitle(R.string.home_warning_runnable_background_title)
        .setMessage(Html.fromHtml(getString(R.string.home_warning_runnable_background_message)))
        .setPositiveButton(R.string.btn_ok, (dialog, which) -> {
            // bump last attempt timestamp by a few hours so that the message is not shown again for a short while.
            final long bumpedLastAttemptTimestamp = System.currentTimeMillis() - CheckElectrumWorker.DELAY_BEFORE_BACKGROUND_WARNING + DateUtils.HOUR_IN_MILLIS * 6;
            prefs.edit()
              .putBoolean(Constants.SETTING_BACKGROUND_CANNOT_RUN_WARNING, false)
              .putLong(Constants.SETTING_ELECTRUM_CHECK_LAST_ATTEMPT_TIMESTAMP, bumpedLastAttemptTimestamp)
              .apply();
          })
        .show();
      final TextView messageView = d.findViewById(android.R.id.message);
      if (messageView != null) {
        messageView.setMovementMethod(LinkMovementMethod.getInstance());
      }
    }
  }

  private void setUpTabs(final Bundle savedInstanceState) {

    final List<Fragment> fragments = new ArrayList<>();
    mReceivePaymentFragment = new ReceivePaymentFragment();
    fragments.add(mReceivePaymentFragment);
    mPaymentsListFragment = new PaymentsListFragment();
    fragments.add(mPaymentsListFragment);
    mChannelsListFragment = new ChannelsListFragment();
    fragments.add(mChannelsListFragment);
    HomePagerAdapter mPagerAdapter = new HomePagerAdapter(getSupportFragmentManager(), fragments);

    mBinding.viewpager.setAdapter(mPagerAdapter);
    mBinding.viewpager.setPageTransformer(true, new TechnicalHelper.ZoomOutPageTransformer());

    mBinding.tabs.setupWithViewPager(mBinding.viewpager);
    for (int i = 0; i < mBinding.tabs.getTabCount(); i++) {
      TabLayout.Tab tab = mBinding.tabs.getTabAt(i);
      if (tab != null) {
        tab.setCustomView(mPagerAdapter.getTabView(i));
      }
    }

    if (savedInstanceState != null && savedInstanceState.containsKey("currentPage")) {
      mBinding.viewpager.setCurrentItem(savedInstanceState.getInt("currentPage"));
    } else {
      Intent intent = getIntent();
      mBinding.viewpager.setCurrentItem(intent.getIntExtra(EXTRA_PAGE, 1));
      mBinding.setCurrentPage(1);
    }

    mBinding.viewpager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
      @Override
      public void onPageSelected(int position) {
        mBinding.setCurrentPage(position);
        animateTabs(position);
      }
    });

    mBinding.tabs.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
      @Override
      public void onGlobalLayout() {
        mBinding.tabs.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        animateTabs(mBinding.getCurrentPage());
      }
    });
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    animateTabs(mBinding.getCurrentPage());
  }

  private void animateTabs(int position) {
    for (int i = 0; i < mBinding.tabs.getTabCount(); i++) {
      TabLayout.Tab tab = mBinding.tabs.getTabAt(i);
      if (tab != null && tab.getCustomView() != null) {
        final View v = tab.getCustomView();
        ((TextView) v.findViewById(R.id.label)).setTextColor(ContextCompat.getColorStateList(getApplicationContext(), i == position ? R.color.white : R.color.primary_light_x2));
        ((ImageView) v.findViewById(R.id.icon)).setColorFilter(ContextCompat.getColor(getApplicationContext(), i == position ? R.color.white : R.color.primary_light_x2));
        if (v.getAnimation() != null) {
          v.clearAnimation();
        }
        v.setPivotX((float) v.getMeasuredWidth() / 2);
        v.setPivotY(v.getMeasuredHeight());
        if (i == position) {
          v.animate().scaleX(1f).scaleY(1f).setDuration(350).setInterpolator(new AccelerateDecelerateInterpolator()).start();
        } else if (v.getScaleX() == 1f) {
          v.animate().scaleX(.75f).scaleY(.75f).setDuration(250).setInterpolator(new AccelerateDecelerateInterpolator()).start();
        } else {
          v.setScaleX(.75f);
          v.setScaleY(.75f);
        }
      }
    }
  }

  private void setUpBalanceInteraction(final SharedPreferences prefs) {
    mBinding.balance.setOnClickListener(v -> {
      boolean displayBalanceAsFiat = WalletUtils.shouldDisplayInFiat(prefs);
      prefs.edit().putBoolean(Constants.SETTING_DISPLAY_IN_FIAT, !displayBalanceAsFiat).commit();
      mBinding.balanceOnchain.refreshUnits();
      mBinding.balanceLightning.refreshUnits();
      mBinding.balanceTotal.refreshUnits();
      if (mPaymentsListFragment.isAdded()) {
        mPaymentsListFragment.refreshList();
      }
      if (mChannelsListFragment.isAdded()) {
        mChannelsListFragment.updateActiveChannelsList();
      }
    });
  }

  @Override
  public void onStart() {
    super.onStart();
    mBinding.balanceTotal.refreshUnits();
    mBinding.balanceOnchain.refreshUnits();
    mBinding.balanceLightning.refreshUnits();
    refreshChannelsBackupWarning();
  }

  @Override
  public void onResume() {
    super.onResume();
    if (checkInit()) {
      if (!EventBus.getDefault().isRegistered(this)) {
        EventBus.getDefault().register(this);
      }
      PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
      // refresh LN balance
      updateElectrumState();
      updateBalance();
      checkBackgroundRunnableWarning();
    }
  }

  @Override
  protected void onNewIntent(final Intent intent) {
    super.onNewIntent(intent);
    readURIIntent(intent);
  }

  private void readURIIntent(final Intent intent) {
    final Uri paymentRequest = intent.getParcelableExtra(EXTRA_PAYMENT_URI);
    if (paymentRequest != null && paymentRequest.getScheme() != null) {
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
      refreshChannelsBackupWarning();
    } else if (Constants.SETTING_BTC_UNIT.equals(key) || Constants.SETTING_SELECTED_FIAT_CURRENCY.equals(key)) {
      mBinding.balanceTotal.refreshUnits();
      mBinding.balanceOnchain.refreshUnits();
      mBinding.balanceLightning.refreshUnits();
    }
  }

  private void refreshChannelsBackupWarning() {
    final boolean isBackupEnabled = BackupHelper.GoogleDrive.isGDriveEnabled(getApplicationContext());
    if (isBackupEnabled) {
      // check that we also have access
      if (BackupHelper.GoogleDrive.getSigninAccount(getApplicationContext()) == null) {
        mBinding.channelsBackupWarning.startAnimation(mBlinkingAnimation);
        mBinding.setChannelsBackupEnabled(false);
        BackupHelper.GoogleDrive.disableGDriveBackup(getApplicationContext());
      } else {
        mBinding.channelsBackupWarning.clearAnimation();
        mBinding.setChannelsBackupEnabled(true);
      }
    } else {
      mBinding.channelsBackupWarning.startAnimation(mBlinkingAnimation);
      mBinding.setChannelsBackupEnabled(false);
    }
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
    mBinding.sendpaymentToggler.animate().rotation(isVisible ? 0 : -45).setInterpolator(new AccelerateDecelerateInterpolator()).setDuration(150).start();
    mBinding.sendpaymentToggler.setBackgroundTintList(ContextCompat.getColorStateList(this, isVisible ? R.color.primary : R.color.grey_4));
    mBinding.sendpaymentActionsList.setVisibility(isVisible ? View.GONE : View.VISIBLE);
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
    intent.putExtra(ScanActivity.EXTRA_SCAN_TYPE, ScanActivity.TYPE_URI_OPEN);
    startActivity(intent);
  }

  public void openChannelWithAcinq(View view) {
    Intent intent = new Intent(getBaseContext(), OpenChannelActivity.class);
    intent.putExtra(OpenChannelActivity.EXTRA_NEW_HOST_URI, Constants.ACINQ_NODE_URI.toString());
    startActivity(intent);
  }

  public void openChannelRandom(View view) {
    Intent intent = new Intent(getBaseContext(), OpenChannelActivity.class);
    intent.putExtra(OpenChannelActivity.EXTRA_USE_DNS_SEED, true);
    startActivity(intent);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleSyncProgressEvent(SyncProgress progress) {
    final int p = (int) (progress.progress() * 100);
    mBinding.setSyncProgress(p);
    if (p >= 100) {
      mBinding.syncProgressIcon.clearAnimation();
    } else {
      if (mBinding.syncProgressIcon.getAnimation() == null || !mBinding.syncProgressIcon.getAnimation().hasStarted() || mBinding.syncProgressIcon.getAnimation().hasEnded()) {
        final Animation mRotatingAnimation = new RotateAnimation(0, -360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,0.5f);
        mRotatingAnimation.setDuration(2000);
        mRotatingAnimation.setRepeatCount(Animation.INFINITE);
        mRotatingAnimation.setInterpolator(new LinearInterpolator());
        mBinding.syncProgressIcon.startAnimation(mRotatingAnimation);
      }
    }
  }

  public void popinSyncProgress(final View view) {
    getCustomDialog(R.string.home_sync_progress_about).setPositiveButton(R.string.btn_ok, null).create().show();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleBalanceEvent(BalanceUpdateEvent event) {
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
    intent.putExtra(PaymentSuccessActivity.EXTRA_PAYMENTSUCCESS_DIRECTION, event.payment.getDirection().toString());
    startActivity(intent);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleBitcoinPaymentFailedEvent(BitcoinPaymentFailedEvent event) {
    Toast.makeText(getApplicationContext(), getString(R.string.payment_toast_failure), Toast.LENGTH_LONG).show();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleChannelUpdateEvent(ChannelUpdateEvent event) {
    if (mReceivePaymentFragment.isAdded()) {
      mReceivePaymentFragment.notifyChannelsUpdate();
    }
    if (mChannelsListFragment.isAdded()) {
      mChannelsListFragment.updateActiveChannelsList();
    }
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

  private void updateElectrumState() {
    final App.ElectrumState state = app.getElectrumState();
    if (state == null || (!state.isConnected && state.address == null)) {
      canSendPayments = false;
      mBinding.setElectrumReady(false);
      final String customElectrumServer = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Constants.CUSTOM_ELECTRUM_SERVER, "");
      if (Strings.isEmptyOrWhitespace(customElectrumServer)) {
        mBinding.electrumState.setText(R.string.home_electrum_establishing);
      } else {
        mBinding.electrumState.setText(getString(R.string.home_electrum_establishing_custom, customElectrumServer));
      }
    } else if (!state.isConnected) {
      canSendPayments = false;
      mBinding.setElectrumReady(false);
      mBinding.electrumState.setText(getString(R.string.home_electrum_syncing, state.address.toString()));
    } else if (Math.abs(System.currentTimeMillis() / 1000L - state.blockTimestamp) > Constants.DESYNC_DIFF_TIMESTAMP_SEC) { // electrum server is late
      canSendPayments = false;
      mBinding.setElectrumReady(false);
      mBinding.electrumState.setText(getString(R.string.home_electrum_syncing, state.address.toString()));
    } else {
      canSendPayments = true;
      mBinding.setElectrumReady(true);
      updateBalance();
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleWalletReadyEvent(ElectrumWallet.WalletReady event) {
    updateElectrumState();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleElectrumReadyEvent(ElectrumClient.ElectrumReady event) {
    updateElectrumState();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleDisconnectionEvent(ElectrumClient.ElectrumDisconnected$ event) {
    updateElectrumState();
  }

  private class HomePagerAdapter extends FragmentStatePagerAdapter {
    private final List<Fragment> mFragmentList;
    private final String[] titles = {getString(R.string.receive_title), getString(R.string.payments_title), getString(R.string.localchannels_title)};
    public int[] icons_resources = {R.drawable.ic_receive_white_24dp, R.drawable.ic_activity_white_24dp, R.drawable.ic_cloud_lightning_white_24dp};

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

    public View getTabView(final int position) {
      final View v = LayoutInflater.from(getApplicationContext()).inflate(R.layout.custom_tab, null);
      ((TextView) v.findViewById(R.id.label)).setText(titles[position]);
      ((ImageView) v.findViewById(R.id.icon)).setImageResource(icons_resources[position]);
      v.setScaleX(.75f);
      v.setScaleY(.75f);
      return v;
    }
  }

}
