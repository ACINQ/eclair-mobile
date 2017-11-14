package fr.acinq.eclair.wallet.activities;

import android.annotation.SuppressLint;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.greenrobot.eventbus.util.ThrowableFailureEvent;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import akka.dispatch.OnComplete;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.wallet.EclairEventService;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.customviews.CoinAmountView;
import fr.acinq.eclair.wallet.events.BitcoinPaymentEvent;
import fr.acinq.eclair.wallet.events.ChannelUpdateEvent;
import fr.acinq.eclair.wallet.events.LNBalanceUpdateEvent;
import fr.acinq.eclair.wallet.events.LNNewChannelFailureEvent;
import fr.acinq.eclair.wallet.events.LNNewChannelOpenedEvent;
import fr.acinq.eclair.wallet.events.LNPaymentEvent;
import fr.acinq.eclair.wallet.events.LNPaymentFailedEvent;
import fr.acinq.eclair.wallet.events.WalletBalanceUpdateEvent;
import fr.acinq.eclair.wallet.fragments.ChannelsListFragment;
import fr.acinq.eclair.wallet.fragments.PaymentsListFragment;
import fr.acinq.eclair.wallet.fragments.ReceivePaymentFragment;
import fr.acinq.eclair.wallet.utils.CoinUtils;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.Validators;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import scala.concurrent.ExecutionContext;

public class HomeActivity extends EclairActivity {

  public static final String EXTRA_PAGE = "fr.acinq.eclair.swordfish.EXTRA_PAGE";
  private static final String TAG = "Home Activity";
  List<Integer> recoveryPositions = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);

  private ViewPager mViewPager;
  private PaymentsListFragment mPaymentsListFragment;
  private ChannelsListFragment mChannelsListFragment;
  private ReceivePaymentFragment mReceivePaymentFragment;
  private ViewGroup mSendButtonsView;
  private ViewGroup mSendButtonsToggleView;
  private FloatingActionButton mSendButton;
  private FloatingActionButton mDisabledSendButton;
  private ViewGroup mOpenChannelsButtonsView;
  private ViewGroup mOpenChannelButtonsToggleView;
  private FloatingActionButton mOpenChannelButton;
  private CoinAmountView mTotalBalanceView;
  private TextView mWalletBalanceView;
  private TextView mLNBalanceView;
  private ViewStub mStubBreakingChanges;
  private ViewStub mStubDisclaimer;
  private View mStubDisclaimerInflated;
  private ViewStub mStubIntro;
  private ViewStub mStubBackup;
  private View mStubBackupInflated;
  private int introStep = 0;

  private Handler mExchangeRateHandler;
  private Runnable mExchangeRateRunnable;
  private JsonObjectRequest mExchangeRateRequest;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setTheme(R.style.AppTheme);
    setContentView(R.layout.activity_home);

    if (app.hasBreakingChanges()) {
      mStubBreakingChanges = findViewById(R.id.home_stub_breaking);
      mStubBreakingChanges.inflate();
      ((TextView) findViewById(R.id.home_breaking_changes_text)).setText(Html.fromHtml(
        getString(R.string.breaking_changes_text)));
    }

    mStubDisclaimer = findViewById(R.id.home_stub_disclaimer);
    mStubIntro = findViewById(R.id.home_stub_intro);
    mStubBackup = findViewById(R.id.home_stub_backup);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(false);
    ab.setDisplayShowTitleEnabled(false);

    mTotalBalanceView = findViewById(R.id.home_balance_total);
    mWalletBalanceView = findViewById(R.id.home_balance_wallet_value);
    mLNBalanceView = findViewById(R.id.home_balance_ln_value);

    mSendButtonsView = findViewById(R.id.home_send_buttons);
    mSendButtonsToggleView = findViewById(R.id.home_send_buttons_toggle);
    mSendButton = findViewById(R.id.home_send_button);
    mDisabledSendButton = findViewById(R.id.home_send_button_disabled);

    mOpenChannelsButtonsView = findViewById(R.id.home_openchannel_buttons);
    mOpenChannelButtonsToggleView = findViewById(R.id.home_openchannel_buttons_toggle);
    mOpenChannelButton = findViewById(R.id.home_openchannel_button);

    mViewPager = findViewById(R.id.home_viewpager);
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
    HomePagerAdapter mPagerAdapter = new HomePagerAdapter(getSupportFragmentManager(), fragments);
    mViewPager.setAdapter(mPagerAdapter);
    if (savedInstanceState != null && savedInstanceState.containsKey("currentPage")) {
      mViewPager.setCurrentItem(savedInstanceState.getInt("currentPage"));
    } else {
      Intent intent = getIntent();
      mViewPager.setCurrentItem(intent.getIntExtra(EXTRA_PAGE, 1));
    }

    EventBus.getDefault().register(this);
    final MilliSatoshi onchainBalanceMsat = new MilliSatoshi(app.getDBHelper().getOnchainBalanceMsat());
    mTotalBalanceView.setAmountMsat(onchainBalanceMsat);
    EventBus.getDefault().postSticky(new WalletBalanceUpdateEvent(package$.MODULE$.millisatoshi2satoshi(onchainBalanceMsat)));

    (new Thread(new Runnable() {
      @Override
      public void run() {
        final SharedPreferences getPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        final boolean showDisclaimer = false;
        final boolean showRecovery = getPrefs.getBoolean(Constants.SETTING_SHOW_RECOVERY, true);
        final boolean showIntro = getPrefs.getBoolean(Constants.SETTING_SHOW_INTRO, true);
        if (showDisclaimer || showRecovery || showIntro) {
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              home_startDisclaimer(showDisclaimer, showRecovery, showIntro, getPrefs);
            }
          });
        }
      }
    })).start();

    app.fAtCurrentBlockHeight().onComplete(new OnComplete<Object>() {
      @Override
      public void onComplete(Throwable throwable, Object o) throws Throwable {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            if (!app.fAtCurrentBlockHeight().isCompleted()) {
              disableSendButton();
            } else {
              enableSendButton();
            }
          }
        });
      }
    }, ExecutionContext.Implicits$.MODULE$.global());

    final RequestQueue queue = Volley.newRequestQueue(this);
    mExchangeRateRequest = new JsonObjectRequest(Request.Method.GET, "https://api.coindesk.com/v1/bpi/currentprice.json", null,
      new Response.Listener<JSONObject>() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onResponse(JSONObject response) {
          try {
            JSONObject bpi = response.getJSONObject("bpi");
            JSONObject eur = bpi.getJSONObject("EUR");
            JSONObject usd = bpi.getJSONObject("USD");
            app.updateExchangeRate(eur.getDouble("rate_float"), usd.getDouble("rate_float"));
          } catch (JSONException e) {
            Log.e("ExchangeRate", "Could not read coindesk response", e);
          }
        }
      }, new Response.ErrorListener() {
      @Override
      public void onErrorResponse(VolleyError error) {
        Log.d("ExchangeRate", "Error when querying coindesk api", error);
      }
    });
    mExchangeRateHandler = new Handler();
    mExchangeRateRunnable = new Runnable() {
      @Override
      public void run() {
        queue.add(mExchangeRateRequest);
        mExchangeRateHandler.postDelayed(this, 5 * 60 * 1000);
      }
    };

  }

  @Override
  public void onResume() {
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this);
    }
    super.onResume();
    if (!app.fAtCurrentBlockHeight().isCompleted()) {
      disableSendButton();
    } else {
      enableSendButton();
    }
    mExchangeRateHandler.post(mExchangeRateRunnable);
    app.requestOnchainBalanceUpdate();
    EclairEventService.postLNBalanceEvent();
  }

  @Override
  public void onPause() {
    super.onPause();
    mExchangeRateHandler.removeCallbacks(mExchangeRateRunnable);
    home_closeSendButtons();
    home_closeOpenChannelButtons();
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
    bundle.putInt("currentPage", mViewPager.getCurrentItem());
    super.onSaveInstanceState(bundle);
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
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
      case R.id.menu_home_networkinfos:
        Intent networkInfosIntent = new Intent(this, NetworkInfosActivity.class);
        startActivity(networkInfosIntent);
        return true;
      case R.id.menu_home_about:
        Intent aboutIntent = new Intent(this, AboutActivity.class);
        startActivity(aboutIntent);
        return true;
      case R.id.menu_home_preferences:
        Intent prefsIntent = new Intent(this, PreferencesActivity.class);
        startActivity(prefsIntent);
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  private void home_startDisclaimer(final boolean showDisclaimer, final boolean showRecovery, final boolean showIntro, final SharedPreferences prefs) {
    if (showDisclaimer) {
      mStubDisclaimerInflated = mStubDisclaimer.inflate();
      findViewById(R.id.home_disclaimer_finish).setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          mStubDisclaimer.setVisibility(View.GONE);
          SharedPreferences.Editor e = prefs.edit();
          e.putBoolean(Constants.SETTING_SHOW_DISCLAIMER, false);
          e.apply();
          home_startBackup(showRecovery, showIntro, prefs);
        }
      });
      ((TextView) findViewById(R.id.home_disclaimer_1_text)).setText(Html.fromHtml(
        getString(R.string.home_disclaimer_1, "TESTNET")));
    } else {
      home_startBackup(showRecovery, showIntro, prefs);
    }
  }

  private void home_startBackup(final boolean showRecovery, final boolean showIntro, final SharedPreferences prefs) {
    if (showRecovery) {
      mStubBackupInflated = mStubBackup.inflate();
      findViewById(R.id.home_backup_finish_button).setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          mStubBackup.setVisibility(View.GONE);
          SharedPreferences.Editor e = prefs.edit();
          e.putBoolean(Constants.SETTING_SHOW_RECOVERY, false);
          e.apply();
          home_startIntro(showIntro, prefs);
        }
      });

      // Allow the user to skip the recovery phase for TESTNET only.
      if (!app.isProduction()) {
        findViewById(R.id.home_backup_skip).setVisibility(View.VISIBLE);
        findViewById(R.id.home_backup_skip).setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            mStubBackup.setVisibility(View.GONE);
            SharedPreferences.Editor e = prefs.edit();
            e.putBoolean(Constants.SETTING_SHOW_RECOVERY, false);
            e.apply();
            home_startIntro(showIntro, prefs);
          }
        });
      }
      try {
        ((TextView) findViewById(R.id.home_backup_text)).setText(Html.fromHtml(
          getString(R.string.home_backup_text, app.getRecoveryPhrase())));
      } catch (Exception e) {
        Log.e(TAG, "Could not generate recovery phrase", e);
        ((TextView) findViewById(R.id.home_backup_text)).setText("Could not generate recovery phrase...");
      }
    } else {
      home_startIntro(showIntro, prefs);
    }
  }

  public void home_initCheckRecoveryPhrase(View view) {
    if (mStubBackupInflated.getVisibility() == View.VISIBLE) {
      Collections.shuffle(recoveryPositions);
      ((TextView) findViewById(R.id.home_backup_check)).setText(Html.fromHtml(
        getString(R.string.home_backup_check, recoveryPositions.get(0) + 1,
          recoveryPositions.get(1) + 1, recoveryPositions.get(2) + 1)));
      findViewById(R.id.home_backup_1).setVisibility(View.GONE);
      findViewById(R.id.home_backup_failed).setVisibility(View.GONE);
      findViewById(R.id.home_backup_2).setVisibility(View.VISIBLE);
    }
  }

  public void home_doCheckRecoveryPhrase(View view) {
    view.clearFocus();
    final InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
    final EditText edit = findViewById(R.id.home_backup_input);
    if (edit.getText() != null) {
      final String[] words = edit.getText().toString().split(" ");
      try {
        if (words.length == 3
          && app.checkWordRecoveryPhrase(recoveryPositions.get(0), words[0])
          && app.checkWordRecoveryPhrase(recoveryPositions.get(1), words[1])
          && app.checkWordRecoveryPhrase(recoveryPositions.get(2), words[2])) {
          findViewById(R.id.home_backup_2).setVisibility(View.GONE);
          findViewById(R.id.home_backup_skip).setVisibility(View.GONE);
          findViewById(R.id.home_backup_success).setVisibility(View.VISIBLE);
          return;
        }
      } catch (Exception e) {
        Log.e(TAG, "Could not check the recovery phrase", e);
      }
    }
    edit.setText("");
    findViewById(R.id.home_backup_2).setVisibility(View.GONE);
    findViewById(R.id.home_backup_1).setVisibility(View.VISIBLE);
    findViewById(R.id.home_backup_failed).setVisibility(View.VISIBLE);
  }

  private void home_startIntro(final boolean showIntro, final SharedPreferences prefs) {
    if (!showIntro) return;
    final View inflatedIntro = mStubIntro.inflate();
    final View introWelcome = findViewById(R.id.home_intro_welcome);
    final View introReceive = findViewById(R.id.home_intro_receive);
    final View introOpenChannel = findViewById(R.id.home_intro_openchannel);
    final View introOpenChannelPatience = findViewById(R.id.home_intro_openchannel_patience);
    final View introSendPayment = findViewById(R.id.home_intro_sendpayment);
    introWelcome.setVisibility(View.VISIBLE);
    inflatedIntro.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        introStep++;
        if (introStep > 4) {
          mStubIntro.setVisibility(View.GONE);
          SharedPreferences.Editor e = prefs.edit();
          e.putBoolean(Constants.SETTING_SHOW_INTRO, false);
          e.apply();
        } else {
          introWelcome.setVisibility(View.GONE);
          introReceive.setVisibility(introStep == 1 ? View.VISIBLE : View.GONE);
          introOpenChannel.setVisibility(introStep == 2 ? View.VISIBLE : View.GONE);
          introOpenChannelPatience.setVisibility(introStep == 3 ? View.VISIBLE : View.GONE);
          introSendPayment.setVisibility(introStep == 4 ? View.VISIBLE : View.GONE);
          if (introStep == 1) {
            mViewPager.setCurrentItem(0);
          } else if (introStep == 2 || introStep == 3) {
            mViewPager.setCurrentItem(2);
          } else {
            mViewPager.setCurrentItem(1);
          }
        }
      }
    });
  }

  public void home_closeApp(View view) {
    finishAndRemoveTask();
    finishAffinity();
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
    mSendButton.animate().rotation(isVisible ? 0 : -90).setInterpolator(new AccelerateDecelerateInterpolator()).setDuration(150).start();
    mSendButton.setBackgroundTintList(ContextCompat.getColorStateList(this, isVisible ? R.color.colorPrimary : R.color.colorGrey_4));
    mSendButtonsToggleView.setVisibility(isVisible ? View.GONE : View.VISIBLE);
  }

  private void enableSendButton() {
    mDisabledSendButton.setVisibility(View.GONE);
    mSendButton.setVisibility(View.VISIBLE);
  }

  private void disableSendButton() {
    mDisabledSendButton.setVisibility(View.VISIBLE);
    mSendButton.setVisibility(View.GONE);
  }

  public void home_closeSendButtons() {
    mSendButton.animate().rotation(0).setInterpolator(new AccelerateDecelerateInterpolator()).setDuration(150).start();
    mSendButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.colorPrimary));
    mSendButtonsToggleView.setVisibility(View.GONE);
  }

  public void home_toggleOpenChannelButtons(View view) {
    boolean isVisible = mOpenChannelButtonsToggleView.getVisibility() == View.VISIBLE;
    mOpenChannelButton.animate().rotation(isVisible ? 0 : 135).setInterpolator(new AccelerateDecelerateInterpolator()).setDuration(150).start();
    mOpenChannelButton.setBackgroundTintList(ContextCompat.getColorStateList(this, isVisible ? R.color.green : R.color.colorGrey_4));
    mOpenChannelButtonsToggleView.setVisibility(isVisible ? View.GONE : View.VISIBLE);
  }

  public void home_closeOpenChannelButtons() {
    mOpenChannelButton.animate().rotation(0).setInterpolator(new AccelerateDecelerateInterpolator()).setDuration(150).start();
    mOpenChannelButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.green));
    mOpenChannelButtonsToggleView.setVisibility(View.GONE);
  }

  public void home_doPasteURI(View view) {
    String uri = readFromClipboard();
    if (Validators.HOST_REGEX.matcher(uri).matches()) {
      Intent intent = new Intent(getBaseContext(), OpenChannelActivity.class);
      intent.putExtra(OpenChannelActivity.EXTRA_NEW_HOST_URI, uri);
      startActivity(intent);
    } else {
      Toast.makeText(this, R.string.home_toast_openchannel_invalid, Toast.LENGTH_SHORT).show();
    }
  }

  public void home_doScanURI(View view) {
    Intent intent = new Intent(this, ScanActivity.class);
    intent.putExtra(ScanActivity.EXTRA_SCAN_TYPE, ScanActivity.TYPE_URI);
    startActivity(intent);
  }

  public void home_doRandomChannel(View view) {
    Intent intent = new Intent(getBaseContext(), OpenChannelActivity.class);
    intent.putExtra(OpenChannelActivity.EXTRA_NEW_HOST_URI,
      WalletUtils.LN_NODES.get(ThreadLocalRandom.current().nextInt(0, WalletUtils.LN_NODES.size())));
    startActivity(intent);
  }

  public void home_doCopyReceptionAddress(View view) {
    mReceivePaymentFragment.copyReceptionAddress();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleLNBalanceEvent(LNBalanceUpdateEvent event) {
    updateBalance();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleWalletBalanceEvent(WalletBalanceUpdateEvent event) {
    updateBalance();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleLNPaymentFailedEvent(LNPaymentFailedEvent event) {
    Intent intent = new Intent(this, PaymentFailureActivity.class);
    intent.putExtra(PaymentFailureActivity.EXTRA_PAYMENTFAILURE_DESC, event.payment.getDescription());
    intent.putExtra(PaymentFailureActivity.EXTRA_PAYMENTFAILURE_AMOUNT, event.payment.getAmountPaidMsat());
    intent.putExtra(PaymentFailureActivity.EXTRA_PAYMENTFAILURE_CAUSE, event.cause);
    intent.putExtra(PaymentFailureActivity.EXTRA_PAYMENTFAILURE_DETAILED_CAUSE, event.detailedCause);
    startActivity(intent);
    mPaymentsListFragment.updateList();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleLNPaymentEvent(LNPaymentEvent event) {
    Intent intent = new Intent(this, PaymentSuccessActivity.class);
    intent.putExtra(PaymentSuccessActivity.EXTRA_PAYMENTSUCCESS_DESC, event.payment.getDescription());
    intent.putExtra(PaymentSuccessActivity.EXTRA_PAYMENTSUCCESS_AMOUNT, event.payment.getAmountPaidMsat());
    startActivity(intent);
    mPaymentsListFragment.updateList();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleBitcoinPaymentEvent(BitcoinPaymentEvent event) {
    mPaymentsListFragment.updateList();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleChannelUpdateEvent(ChannelUpdateEvent event) {
    mChannelsListFragment.updateList();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleNewChannelSuccessfullyOpened(LNNewChannelOpenedEvent event) {
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleNewChannelFailed(LNNewChannelFailureEvent event) {
    Toast.makeText(this, getString(R.string.home_toast_openchannel_failed) + event.cause, Toast.LENGTH_LONG).show();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleThrowableEvent(ThrowableFailureEvent event) {
    Log.e(TAG, "Event failed", event.getThrowable());
    Toast.makeText(this, getString(R.string.generic_error), Toast.LENGTH_LONG);
  }

  @SuppressLint("SetTextI18n")
  private void updateBalance() {
    LNBalanceUpdateEvent lnBalanceEvent = EventBus.getDefault().getStickyEvent(LNBalanceUpdateEvent.class);
    long lnBalance = lnBalanceEvent == null ? 0 : lnBalanceEvent.total().amount();
    WalletBalanceUpdateEvent walletBalanceEvent = EventBus.getDefault().getStickyEvent(WalletBalanceUpdateEvent.class);
    long walletBalance = walletBalanceEvent == null ? 0 : package$.MODULE$.satoshi2millisatoshi(walletBalanceEvent.walletBalance).amount();
    mTotalBalanceView.setAmountMsat(new MilliSatoshi(lnBalance + walletBalance));
    mWalletBalanceView.setText(CoinUtils.formatAmountMilliBtc(new MilliSatoshi(walletBalance)) + " mBTC");
    mLNBalanceView.setText(CoinUtils.formatAmountMilliBtc(new MilliSatoshi(lnBalance)) + " mBTC");
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
