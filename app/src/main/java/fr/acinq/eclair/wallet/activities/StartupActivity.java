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

import android.content.Intent;
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.view.View;

import com.google.common.io.Files;
import com.typesafe.config.ConfigFactory;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.TimeoutException;

import akka.actor.ActorRef;
import akka.actor.Props;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.eclair.DBCompatChecker;
import fr.acinq.eclair.Kit;
import fr.acinq.eclair.Setup;
import fr.acinq.eclair.blockchain.electrum.ElectrumEclairWallet;
import fr.acinq.eclair.channel.ChannelEvent;
import fr.acinq.eclair.payment.PaymentLifecycle;
import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.EclairEventService;
import fr.acinq.eclair.wallet.PaymentSupervisor;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityStartupBinding;
import fr.acinq.eclair.wallet.databinding.StubUsageDisclaimerBinding;
import fr.acinq.eclair.wallet.fragments.PinDialog;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import scala.Option;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

public class StartupActivity extends EclairActivity implements EclairActivity.EncryptSeedCallback {

  private static final String TAG = "StartupActivity";
  private ActivityStartupBinding mBinding;
  private StubUsageDisclaimerBinding mDisclaimerBinding;
  private PinDialog pinDialog;
  public final static String ORIGIN = BuildConfig.APPLICATION_ID + "ORIGIN";
  public final static String ORIGIN_EXTRA = BuildConfig.APPLICATION_ID + "ORIGIN_EXTRA";
  private static final HashSet<Integer> BREAKING_VERSIONS = new HashSet<>(Arrays.asList(14));

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_startup);
    mBinding.stubDisclaimer.setOnInflateListener((stub, inflated) -> mDisclaimerBinding = DataBindingUtil.bind(inflated));
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this);
    }
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    final File datadir = new File(app.getFilesDir(), Constants.ECLAIR_DATADIR);
    startCheckup(datadir, prefs);
  }

  @Override
  protected void onDestroy() {
    EventBus.getDefault().unregister(this);
    super.onDestroy();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void processStartupFinish(StartupCompleteEvent event) {
    switch(event.status) {
      case StartupTask.SUCCESS:
        if (app.appKit != null) {
          PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit()
            .putInt(Constants.SETTING_LAST_USED_VERSION, BuildConfig.VERSION_CODE).apply();
          goToHome();
        } else {
          // empty appkit, something went wrong.
          showError(getString(R.string.start_error_improper));
          new Handler().postDelayed(() -> startNode(new File(app.getFilesDir(), Constants.ECLAIR_DATADIR)), 1400);
        }
        break;
      case StartupTask.WRONG_PWD:
        app.pin.set(null);
        showError(getString(R.string.start_error_wrong_password));
        new Handler().postDelayed(() -> startNode(new File(app.getFilesDir(), Constants.ECLAIR_DATADIR)), 1400);
        break;
      case StartupTask.UNREADABLE:
        showError(getString(R.string.start_error_unreadable_seed), true);
        break;
      case StartupTask.TIMEOUT_ERROR:
        showError(getString(R.string.start_error_timeout), true);
        break;
      default:
        showError(getString(R.string.start_error_generic), true);
        break;
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void processStartupProgress(StartupProgressEvent event) {
    mBinding.startupLog.setText(event.message);
  }

  private void showBreaking() {
    mBinding.startupError.setVisibility(View.VISIBLE);
    mBinding.startupErrorText.setText(Html.fromHtml(getString(R.string.start_error_breaking_changes)));
  }

  private void showError(final String message, final boolean showFAQ) {
    mBinding.startupError.setVisibility(View.VISIBLE);
    mBinding.startupErrorFaq.setVisibility(showFAQ ? View.VISIBLE : View.GONE);
    mBinding.startupErrorText.setText(message);
  }

  private void showError(final String message) {
    showError(message, false);
  }

  private void goToHome() {
    finish();
    final Intent originIntent = getIntent();
    final Intent homeIntent = new Intent(getBaseContext(), HomeActivity.class);
    if (originIntent.hasExtra(ORIGIN)) {
      homeIntent.putExtra(ORIGIN, originIntent.getStringExtra(ORIGIN));
      homeIntent.putExtra(ORIGIN_EXTRA, originIntent.getStringExtra(ORIGIN_EXTRA));
    }
    homeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
    homeIntent.putExtra(HomeActivity.EXTRA_PAYMENT_URI, getIntent().getData());
    startActivity(homeIntent);
  }

  public void closeApp(View view) {
    finishAndRemoveTask();
    finishAffinity();
  }

  private void startCheckup(final File datadir, final SharedPreferences prefs) {
    if (prefs.getBoolean(Constants.SETTING_SHOW_DISCLAIMER, true)) {
      if (!mBinding.stubDisclaimer.isInflated()) {
        mBinding.stubDisclaimer.getViewStub().inflate();
        mDisclaimerBinding.disclaimerFinish.setOnClickListener(v -> {
          mDisclaimerBinding.getRoot().setVisibility(View.GONE);
          prefs.edit().putBoolean(Constants.SETTING_SHOW_DISCLAIMER, false).apply();
          checkAppVersion(datadir, prefs);
        });
        mDisclaimerBinding.disclaimerText.setText(Html.fromHtml(getString(R.string.disclaimer_1)));
      }
    } else {
      checkAppVersion(datadir, prefs);
    }
  }

  private void checkAppVersion(final File datadir, final SharedPreferences prefs) {
    final int lastUsedVersion = prefs.getInt(Constants.SETTING_LAST_USED_VERSION, 0);
    final boolean eclairStartedOnce = (datadir.exists() && datadir.isDirectory() && new File(datadir, "eclair.sqlite").exists());
    final boolean isFreshInstall = lastUsedVersion == 0 && !eclairStartedOnce;
    Log.d(TAG, "last used version = " + lastUsedVersion);
    Log.d(TAG, "has eclair started once ? " + eclairStartedOnce);
    Log.d(TAG, "fresh install ? " + isFreshInstall);
    if (lastUsedVersion < BuildConfig.VERSION_CODE && !isFreshInstall) {
      if (BREAKING_VERSIONS.contains(BuildConfig.VERSION_CODE)) {
        showBreaking();
        return;
      }
    }
    if (!isFreshInstall && lastUsedVersion <= 15) {
      if ("testnet".equals(BuildConfig.CHAIN)) {
        migrateTestnetSqlite(datadir);
      }
    }
    checkWalletInit(datadir);
  }

  private void migrateTestnetSqlite(final File datadir) {
    final File eclairSqlite = new File(datadir, "eclair.sqlite");
    final File testnetDir = new File(datadir, "testnet");
    if (eclairSqlite.exists()) {
      if (!testnetDir.exists()) {
        testnetDir.mkdir();
      }
      final File eclairSqliteInTestnet = new File(testnetDir, "eclair.sqlite");
      if (!eclairSqliteInTestnet.exists()) {
        try {
          Files.move(eclairSqlite, eclairSqliteInTestnet);
          Log.i(TAG, "moved eclair.sqlite to testnet dir");
        } catch (IOException e) {
          showError(getString(R.string.start_error_generic));
        }
      }
    }
  }

  private void checkWalletInit(final File datadir) {
    if (!datadir.exists()) {
      if (!mBinding.stubPickInitWallet.isInflated()) {
        mBinding.stubPickInitWallet.getViewStub().inflate();
      }
    } else {
      final File unencryptedSeedFile = new File(datadir, WalletUtils.UNENCRYPTED_SEED_NAME);
      final File encryptedSeedFile = new File(datadir, WalletUtils.SEED_NAME);
      if (unencryptedSeedFile.exists() && !encryptedSeedFile.exists()) {
        Log.i(TAG, "non encrypted seed file found in datadir, encryption is required");
        try {
          final byte[] unencryptedSeed = Files.toByteArray(unencryptedSeedFile);
          showError(getString(R.string.start_error_unencrypted));
          new Handler().postDelayed(() -> encryptWallet(this, false, datadir, unencryptedSeed), 2500);
        } catch (IOException e) {
          Log.e(TAG, "Could not encrypt unencrypted seed", e);
        }
      } else if (unencryptedSeedFile.exists() && encryptedSeedFile.exists()) {
        // encrypted seed is the reference
        unencryptedSeedFile.delete();
        startNode(datadir);
      } else if (encryptedSeedFile.exists()) {
        startNode(datadir);
      } else {
        if (!mBinding.stubPickInitWallet.isInflated()) {
          mBinding.stubPickInitWallet.getViewStub().inflate();
        }
      }
    }
  }

  /**
   * Starts up the eclair node if needed
   */
  private void startNode(final File datadir) {
    mBinding.startupError.setVisibility(View.GONE);

    if (mBinding.stubDisclaimer.isInflated()) {
      mBinding.stubDisclaimer.getRoot().setVisibility(View.GONE);
    }
    if (mBinding.stubPickInitWallet.isInflated()) {
      mBinding.stubPickInitWallet.getRoot().setVisibility(View.GONE);
    }

    if (app.appKit == null) {
      if (datadir.exists() && !datadir.canRead()) {
        Log.e(TAG, "datadir is not readable. Aborting startup");
        showError(getString(R.string.start_error_datadir_unreadable), true);
      } else if (datadir.exists() && !datadir.isDirectory()) {
        Log.e(TAG, "datadir is not a directory. Aborting startup");
        showError(getString(R.string.start_error_datadir_not_directory), true);
      } else {
        final String currentPassword = app.pin.get();
        if (currentPassword == null) {
          if (pinDialog != null) {
            pinDialog.dismiss();
          }
          pinDialog = new PinDialog(StartupActivity.this, R.style.CustomAlertDialog, new PinDialog.PinDialogCallback() {
            @Override
            public void onPinConfirm(final PinDialog dialog, final String pinValue) {
              new StartupTask(pinValue).execute(app);
              dialog.dismiss();
            }
            @Override
            public void onPinCancel(PinDialog dialog) {}
          }, "Enter password to unlock");
          pinDialog.setCanceledOnTouchOutside(false);
          pinDialog.setCancelable(false);
          pinDialog.show();
        } else {
          new StartupTask(currentPassword).execute(app);
        }
      }
    } else {
      // core is started, go to home and use it
      goToHome();
    }
  }

  @Override
  protected void onPause() {
    if (pinDialog != null) {
      pinDialog.dismiss();
    }
    super.onPause();
  }

  public void pickImportExistingWallet(View view) {
    Intent intent = new Intent(getBaseContext(), ImportWalletActivity.class);
    startActivity(intent);
  }

  public void pickCreateNewWallet(View view) {
    Intent intent = new Intent(getBaseContext(), CreateWalletRecoveryActivity.class);
    startActivity(intent);
  }

  public void openFAQ(View view) {
    Intent faqIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ACINQ/eclair-wallet/wiki/FAQ"));
    startActivity(faqIntent);
  }

  @Override
  public void onEncryptSeedFailure(String message) {
    showError(message);
    new Handler().postDelayed(() -> checkWalletInit(new File(app.getFilesDir(), Constants.ECLAIR_DATADIR)), 1400);
  }

  @Override
  public void onEncryptSeedSuccess() {
    checkWalletInit(new File(app.getFilesDir(), Constants.ECLAIR_DATADIR));
  }

  /**
   * Starts the eclair node in an asynchronous task.
   * When the task is finished, executes `processStartupFinish` in StartupActivity.
   */
  private static class StartupTask extends AsyncTask<App, String, Integer> {
    private static final String TAG = "StartupTask";

    private final static int SUCCESS = 0;
    private final static int WRONG_PWD = 1;
    private final static int UNREADABLE = 2;
    private final static int GENERIC_ERROR = 3;
    private final static int TIMEOUT_ERROR = 4;
    private final String password;

    private StartupTask(String password) {
      this.password = password;
    }

    @Override
    protected void onProgressUpdate(String... status) {
      super.onProgressUpdate(status);
      EventBus.getDefault().post(new StartupProgressEvent(status[0]));
    }

    @Override
    protected Integer doInBackground(App... params) {
      try {
        App app = params[0];
        final File datadir = new File(app.getFilesDir(), Constants.ECLAIR_DATADIR);
        Log.d(TAG, "Accessing Eclair Setup with datadir " + datadir.getAbsolutePath());

        publishProgress("reading seed");
        final BinaryData seed = BinaryData.apply(new String(WalletUtils.readSeedFile(datadir, password)));
        app.pin.set(password);

        publishProgress("initializing system");
        app.checkupInit();

        Class.forName("org.sqlite.JDBC");
        publishProgress("setting up eclair");
        final Setup setup = new Setup(datadir, Option.apply(null), ConfigFactory.empty(), app.system, Option.apply(seed));

        // gui and electrum supervisor actors
        ActorRef guiUpdater = app.system.actorOf(Props.create(EclairEventService.class, app.getDBHelper()));
        setup.system().eventStream().subscribe(guiUpdater, ChannelEvent.class);
        setup.system().eventStream().subscribe(guiUpdater, PaymentLifecycle.PaymentResult.class);
        app.system.actorOf(Props.create(PaymentSupervisor.class, app.getDBHelper()), "payments");

        publishProgress("starting core");
        Future<Kit> fKit = setup.bootstrap();
        Kit kit = Await.result(fKit, Duration.create(60, "seconds"));
        ElectrumEclairWallet electrumWallet = (ElectrumEclairWallet) kit.wallet();
        publishProgress("checking compatibility");
        boolean isDBCompatible = true;
        try {
          DBCompatChecker.checkDBCompatibility(setup.nodeParams());
        } catch (Exception e) {
          isDBCompatible = false;
        }
        publishProgress("done");
        app.appKit = new App.AppKit(electrumWallet, kit, isDBCompatible);
        return SUCCESS;
      } catch (GeneralSecurityException e) {
        return WRONG_PWD;
      } catch (IOException e) {
        return UNREADABLE;
      } catch (TimeoutException e) {
        return TIMEOUT_ERROR;
      } catch (Exception e) {
        Log.e(TAG, "Failed to start eclair", e);
        return GENERIC_ERROR;
      }
    }

    @Override
    protected void onPostExecute(Integer status) {
      EventBus.getDefault().post(new StartupCompleteEvent(status));
    }
  }

  public static class StartupCompleteEvent {
    public final int status;

    StartupCompleteEvent(int status) {
      this.status = status;
    }
  }

  public static class StartupProgressEvent {
    final String message;

    StartupProgressEvent(String message) {
      this.message = message;
    }
  }
}
