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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.dispatch.Futures;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.DeterministicWallet;
import fr.acinq.eclair.DBCompatChecker;
import fr.acinq.eclair.Kit;
import fr.acinq.eclair.Setup;
import fr.acinq.eclair.blockchain.electrum.ElectrumEclairWallet;
import fr.acinq.eclair.channel.ChannelEvent;
import fr.acinq.eclair.crypto.LocalKeyManager;
import fr.acinq.eclair.payment.PaymentLifecycle;
import fr.acinq.eclair.router.SyncProgress;
import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.BackupScheduler;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.EclairEventService;
import fr.acinq.eclair.wallet.PaymentSupervisor;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityStartupBinding;
import fr.acinq.eclair.wallet.fragments.PinDialog;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import scala.Option;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

public class StartupActivity extends EclairActivity implements EclairActivity.EncryptSeedCallback {

  private static final String TAG = "StartupActivity";
  private ActivityStartupBinding mBinding;
  private PinDialog mPinDialog;
  public final static String ORIGIN = BuildConfig.APPLICATION_ID + "ORIGIN";
  public final static String ORIGIN_EXTRA = BuildConfig.APPLICATION_ID + "ORIGIN_EXTRA";
  private static final HashSet<Integer> BREAKING_VERSIONS = new HashSet<>(Arrays.asList(14));

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_startup);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this);
    }
    checkup();
  }

  @Override
  protected void onPause() {
    if (mPinDialog != null) {
      mPinDialog.dismiss();
    }
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    EventBus.getDefault().unregister(this);
    super.onDestroy();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void processStartupFinish(StartupCompleteEvent event) {
    final File datadir = new File(app.getFilesDir(), Constants.ECLAIR_DATADIR);
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    switch (event.status) {
      case StartupTask.SUCCESS:
        if (app.appKit != null) {
          prefs.edit()
            .putBoolean(Constants.SETTING_HAS_STARTED_ONCE, true)
            .putInt(Constants.SETTING_LAST_USED_VERSION, BuildConfig.VERSION_CODE).apply();
          goToHome();
        } else {
          // empty appkit, something went wrong.
          showError(getString(R.string.start_error_improper));
          new Handler().postDelayed(() -> startNode(datadir, prefs), 1400);
        }
        break;
      case StartupTask.TIMEOUT_ERROR:
        app.pin.set(null);
        app.seedHash.set(null);
        showError(getString(R.string.start_error_timeout), true);
        break;
      default:
        app.pin.set(null);
        app.seedHash.set(null);
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

  private void checkup() {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    final File datadir = new File(app.getFilesDir(), Constants.ECLAIR_DATADIR);
    // check version, apply migration script if required
    if (!checkAppVersion(datadir, prefs)) return;
    // check that wallet data are correct
    if (!checkWalletDatadir(datadir)) return;

    startNode(datadir, prefs);
  }

  private boolean checkAppVersion(final File datadir, final SharedPreferences prefs) {
    final int lastUsedVersion = prefs.getInt(Constants.SETTING_LAST_USED_VERSION, 0);
    final boolean eclairStartedOnce = (datadir.exists() && datadir.isDirectory()
      && WalletUtils.getEclairDBFile(getApplicationContext()).exists());
    final boolean isFreshInstall = lastUsedVersion == 0 && !eclairStartedOnce;
    Log.d(TAG, "last used version = " + lastUsedVersion);
    Log.d(TAG, "has eclair started once ? " + eclairStartedOnce);
    Log.d(TAG, "fresh install ? " + isFreshInstall);
    if (lastUsedVersion < BuildConfig.VERSION_CODE && !isFreshInstall) {
      if (BREAKING_VERSIONS.contains(BuildConfig.VERSION_CODE)) {
        showBreaking();
        return false;
      }
    }
    if (!isFreshInstall && lastUsedVersion <= 15) {
      if ("testnet".equals(BuildConfig.CHAIN)) {
        migrateTestnetSqlite(datadir);
      }
    }
    return true;
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

  private boolean checkWalletDatadir(final File datadir) {
    if (!datadir.exists()) {
      if (!mBinding.stubPickInitWallet.isInflated()) {
        mBinding.stubPickInitWallet.getViewStub().inflate();
      }
      return false;
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
        return false;
      } else if (unencryptedSeedFile.exists() && encryptedSeedFile.exists()) {
        // encrypted seed is the reference
        unencryptedSeedFile.delete();
        return false;
      } else if (encryptedSeedFile.exists()) {
        return true;
      } else {
        if (!mBinding.stubPickInitWallet.isInflated()) {
          mBinding.stubPickInitWallet.getViewStub().inflate();
        }
        return false;
      }
    }
  }

  private boolean checkChannelsBackupRestore() {
    if (!WalletUtils.getEclairDBFile(getApplicationContext()).exists()) {
      Log.i(TAG, "could not find eclair DB file in datadir, attempting to restore backup");
      final int connectionResult = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getApplicationContext());
      if (connectionResult != ConnectionResult.SUCCESS) {
        Log.i(TAG, "Google play services are not available (code " + connectionResult + ")");
        return true;
      } else {
        final Intent intent = new Intent(getBaseContext(), RestoreChannelsBackupActivity.class);
        startActivity(intent);
        return false;
      }
    }
    return true;
  }

  private boolean checkChannelsBackup(final SharedPreferences prefs) {
    final int connectionResult = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getApplicationContext());
    if (connectionResult == ConnectionResult.SUCCESS
      && !prefs.getBoolean(Constants.SETTING_CHANNELS_BACKUP_SEEN_ONCE, false)
      && !prefs.getBoolean(Constants.SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_ENABLED, false)) {
      final Intent intent = new Intent(getBaseContext(), EnableChannelsBackupActivity.class);
      intent.putExtra(EnableChannelsBackupActivity.FROM_STARTUP, true);
      startActivity(intent);
      return false;
    }
    return true;
  }

  /**
   * Starts up the eclair node if needed
   */
  private void startNode(final File datadir, final SharedPreferences prefs) {
    mBinding.startupError.setVisibility(View.GONE);
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
          if (mPinDialog != null) {
            mPinDialog.dismiss();
          }
          mPinDialog = new PinDialog(StartupActivity.this, R.style.FullScreenDialog,
            new PinDialog.PinDialogCallback() {
              @Override
              public void onPinConfirm(final PinDialog dialog, final String pinValue) {
                launchStartupTask(datadir, pinValue, prefs);
                dialog.dismiss();
              }

              @Override
              public void onPinCancel(PinDialog dialog) {
              }
            }, getString(R.string.start_enter_password));
          mPinDialog.setCanceledOnTouchOutside(false);
          mPinDialog.setCancelable(false);
          mPinDialog.show();
        } else {
          launchStartupTask(datadir, currentPassword, prefs);

        }
      }
    } else {
      // core is started, go to home and use it
      goToHome();
    }
  }

  private void launchStartupTask(final File datadir, final String password, final SharedPreferences prefs) {
    mBinding.startupLog.setText(getString(R.string.start_log_reading_seed));
    new Thread() {
      @Override
      public void run() {
        try {
          final BinaryData seed = BinaryData.apply(new String(WalletUtils.readSeedFile(datadir, password)));
          final DeterministicWallet.ExtendedPrivateKey pk = DeterministicWallet.derivePrivateKey(
            DeterministicWallet.generate(seed.data()), LocalKeyManager.nodeKeyBasePath(WalletUtils.getChainHash()));
          app.pin.set(password);
          app.seedHash.set(pk.privateKey().publicKey().hash160().toString());

          if (!prefs.getBoolean(Constants.SETTING_HAS_STARTED_ONCE, false)) {
            // restore channels only if the seed itself was restored
            if (prefs.getInt(Constants.SETTING_WALLET_ORIGIN, 0) == Constants.WALLET_ORIGIN_RESTORED_FROM_SEED
              && !prefs.getBoolean(Constants.SETTING_CHANNELS_RESTORE_DONE, false)) {
              if (!checkChannelsBackupRestore()) return;
            }

            // check that a backup type has been set and required authorizations are granted
            if (!prefs.getBoolean(Constants.SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_ENABLED, false)) {
              if (!checkChannelsBackup(prefs)) return;
            }
          }
          new StartupTask().execute(app, seed);
        } catch (GeneralSecurityException e) {
          Log.w(TAG, "attempted to read seed with wrong password");
          app.pin.set(null);
          app.seedHash.set(null);
          runOnUiThread(() -> {
            showError(getString(R.string.start_error_wrong_password));
            new Handler().postDelayed(() -> startNode(datadir, prefs), 1400);
          });
        } catch (Throwable t) {
          Log.e(TAG, "seed is unreadable", t);
          app.pin.set(null);
          app.seedHash.set(null);
          runOnUiThread(() -> {
            showError(getString(R.string.start_error_unreadable_seed), true);
          });
        }
      }
    }.start();

  }

  public void pickImportExistingWallet(View view) {
    Intent intent = new Intent(getBaseContext(), ImportWalletActivity.class);
    startActivity(intent);
  }

  public void pickCreateNewWallet(View view) {
    Intent intent = new Intent(getBaseContext(), CreateWalletFromScratchActivity.class);
    startActivity(intent);
  }

  public void openFAQ(View view) {
    Intent faqIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ACINQ/eclair-wallet/wiki/FAQ"));
    startActivity(faqIntent);
  }

  @Override
  public void onEncryptSeedFailure(String message) {
    showError(message);
    new Handler().postDelayed(() -> checkWalletDatadir(new File(app.getFilesDir(), Constants.ECLAIR_DATADIR)), 1400);
  }

  @Override
  public void onEncryptSeedSuccess() {
    checkWalletDatadir(new File(app.getFilesDir(), Constants.ECLAIR_DATADIR));
  }

  /**
   * Starts the eclair node in an asynchronous task.
   * When the task is finished, executes `processStartupFinish` in StartupActivity.
   */
  private static class StartupTask extends AsyncTask<Object, String, Integer> {
    private static final String TAG = "StartupTask";

    private final static int SUCCESS = 0;
    private final static int GENERIC_ERROR = 3;
    private final static int TIMEOUT_ERROR = 4;

    @Override
    protected void onProgressUpdate(String... status) {
      super.onProgressUpdate(status);
      EventBus.getDefault().post(new StartupProgressEvent(status[0]));
    }

    @Override
    protected Integer doInBackground(Object... params) {
      try {
        App app = (App) params[0];
        final BinaryData seed = (BinaryData) params[1];
        final File datadir = new File(app.getFilesDir(), Constants.ECLAIR_DATADIR);
        Log.d(TAG, "Accessing Eclair Setup with datadir " + datadir.getAbsolutePath());

        publishProgress("initializing system");
        app.checkupInit();

        Class.forName("org.sqlite.JDBC");
        publishProgress("setting up eclair");
        final Setup setup = new Setup(datadir, ConfigFactory.empty(), Option.apply(seed), app.system);

        // backup scheduler actor
        ActorSelection backupActorFutureSelection = app.system.actorSelection("BackupScheduler");
        Future<ActorRef> backupActorFuture = backupActorFutureSelection.resolveOne(FiniteDuration.apply(1, TimeUnit.SECONDS))
          .fallbackTo(Futures.successful(app.system.actorOf(Props.create(BackupScheduler.class), "BackupScheduler")));
        final ActorRef backupScheduler = Await.result(backupActorFuture, Duration.create(2, "seconds"));

        // gui updater actor
        ActorSelection guiActorFutureSelection = app.system.actorSelection("GuiUpdater");
        Future<ActorRef> guiActorFuture = guiActorFutureSelection.resolveOne(FiniteDuration.apply(1, TimeUnit.SECONDS))
          .fallbackTo(Futures.successful(app.system.actorOf(Props.create(EclairEventService.class, app.getDBHelper(), backupScheduler), "GuiUpdater")));
        final ActorRef guiUpdater = Await.result(guiActorFuture, Duration.create(2, "seconds"));
        app.system.eventStream().subscribe(guiUpdater, ChannelEvent.class);
        app.system.eventStream().subscribe(guiUpdater, SyncProgress.class);
        app.system.eventStream().subscribe(guiUpdater, PaymentLifecycle.PaymentResult.class);

        // electrum payment supervisor actor
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
