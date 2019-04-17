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

import akka.actor.ActorRef;
import akka.actor.Props;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Html;
import android.view.View;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.common.io.Files;
import fr.acinq.bitcoin.DeterministicWallet;
import fr.acinq.eclair.Kit;
import fr.acinq.eclair.Setup;
import fr.acinq.eclair.blockchain.electrum.ElectrumEclairWallet;
import fr.acinq.eclair.channel.ChannelEvent;
import fr.acinq.eclair.crypto.LocalKeyManager;
import fr.acinq.eclair.payment.PaymentEvent;
import fr.acinq.eclair.payment.PaymentLifecycle;
import fr.acinq.eclair.router.SyncProgress;
import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.actors.ElectrumSupervisor;
import fr.acinq.eclair.wallet.actors.NodeSupervisor;
import fr.acinq.eclair.wallet.actors.RefreshScheduler;
import fr.acinq.eclair.wallet.databinding.ActivityStartupBinding;
import fr.acinq.eclair.wallet.fragments.PinDialog;
import fr.acinq.eclair.wallet.services.BackupUtils;
import fr.acinq.eclair.wallet.services.CheckElectrumWorker;
import fr.acinq.eclair.wallet.services.NetworkSyncWorker;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.EclairException;
import fr.acinq.eclair.wallet.utils.EncryptedBackup;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import fr.acinq.eclair.wire.NodeAddress$;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;
import scala.Option;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scodec.bits.ByteVector;
import scodec.bits.ByteVector$;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class StartupActivity extends EclairActivity implements EclairActivity.EncryptSeedCallback {

  private final Logger log = LoggerFactory.getLogger(StartupActivity.class);

  private ActivityStartupBinding mBinding;
  private PinDialog mPinDialog;
  private boolean isStartingNode = false;
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
    if (isStartingNode) {
      log.debug("node is starting, wait for resolution...");
    } else {
      checkup();
    }
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

  private void showBreaking() {
    mBinding.startupError.setVisibility(View.VISIBLE);
    mBinding.startupErrorText.setText(Html.fromHtml(getString(R.string.start_error_breaking_changes)));
  }

  private void showError(final String message, final boolean showRestart, final boolean showFAQ) {
    mBinding.startupError.setVisibility(View.VISIBLE);
    mBinding.startupErrorFaq.setVisibility(showFAQ ? View.VISIBLE : View.GONE);
    mBinding.startupRestart.setVisibility(showRestart ? View.VISIBLE : View.GONE);
    mBinding.startupErrorText.setText(message);
  }

  private void showError(final String message) {
    showError(message, false, false);
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

  private void checkup() {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    final File datadir = new File(app.getFilesDir(), Constants.ECLAIR_DATADIR);
    // check version, apply migration script if required
    if (!checkAppVersion(datadir, prefs)) {
      log.info("check version failed");
      return;
    }
    // check that wallet data are correct
    if (!checkWalletDatadir(datadir)) {
      log.debug("wallet datadir checked failed");
      return;
    }

    startNode(datadir, prefs);
  }

  @SuppressLint("ApplySharedPref")
  private boolean checkAppVersion(final File datadir, final SharedPreferences prefs) {
    final int lastUsedVersion = prefs.getInt(Constants.SETTING_LAST_USED_VERSION, 0);
    final boolean startedOnce = prefs.getBoolean(Constants.SETTING_HAS_STARTED_ONCE, false);
    if (lastUsedVersion > 0 && startedOnce) { // only for
      if (lastUsedVersion < BuildConfig.VERSION_CODE) {
        if (BREAKING_VERSIONS.contains(BuildConfig.VERSION_CODE)) {
          log.error("version {} cannot migrate from {}", BuildConfig.VERSION_CODE, lastUsedVersion);
          showBreaking();
          return false;
        }
      }
      // migration scripts based on last used version
      if (lastUsedVersion <= 15 && "testnet".equals(BuildConfig.CHAIN)) {
        // version 16 breaks the application's data folder structure
        migrateTestnetSqlite(datadir);
      }
      if (lastUsedVersion <= 28) {
        log.debug("migrating network database from version {} <= 28", lastUsedVersion);
        // if last used version is 28 or earlier, we need to reset the network DB due to changes in DB structure
        // see https://github.com/ACINQ/eclair/pull/738
        // note that only the android branch breaks compatibility, due to the absence of a blob 'data' column
        try {
          if (WalletUtils.getNetworkDBFile(getApplicationContext()).exists() && !WalletUtils.getNetworkDBFile(getApplicationContext()).delete()) {
            log.warn("failed to clear network database for <v28 migration");
          }
        } catch (Throwable t) {
          log.error("could not clear network database for <v28 migration", t);
        }
      }
    }
    prefs.edit().putInt(Constants.SETTING_LAST_USED_VERSION, BuildConfig.VERSION_CODE).commit();
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
        } catch (IOException e) {
          showError(getString(R.string.start_error_generic), true, false);
        }
      }
    }
  }

  private boolean checkWalletDatadir(final File datadir) {
    mBinding.startupLog.setText("");
    if (!datadir.exists()) {
      if (!mBinding.stubPickInitWallet.isInflated()) {
        mBinding.stubPickInitWallet.getViewStub().inflate();
      }
      return false;
    } else {
      final File unencryptedSeedFile = new File(datadir, WalletUtils.UNENCRYPTED_SEED_NAME);
      final File encryptedSeedFile = new File(datadir, WalletUtils.SEED_NAME);
      if (unencryptedSeedFile.exists() && !encryptedSeedFile.exists()) {
        try {
          final byte[] unencryptedSeed = Files.toByteArray(unencryptedSeedFile);
          showError(getString(R.string.start_error_unencrypted));
          new Handler().postDelayed(() -> encryptWallet(this, false, datadir, unencryptedSeed), 2500);
        } catch (IOException e) {
          log.error("could not encrypt unencrypted seed", e);
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

  private boolean shouldRestoreChannelsBackup(final SharedPreferences prefs) {
    if (prefs.getInt(Constants.SETTING_WALLET_ORIGIN, Constants.WALLET_ORIGIN_RESTORED_FROM_SEED) == Constants.WALLET_ORIGIN_RESTORED_FROM_SEED
      && !prefs.getBoolean(Constants.SETTING_CHANNELS_RESTORE_DONE, false)) {
      if (WalletUtils.getEclairDBFile(getApplicationContext()).exists()) {
        log.warn("inconsistent state: wallet file exists but prefs want to restore backup");
        return false;
      } else {
        return true;
      }
    } else {
      return false;
    }
  }

  /**
   * Starts up the eclair node if needed
   */
  private void startNode(final File datadir, final SharedPreferences prefs) {
    mBinding.startupError.setVisibility(View.GONE);
    if (mBinding.stubPickInitWallet.isInflated()) {
      mBinding.stubPickInitWallet.getRoot().setVisibility(View.GONE);
    }
    if (!isAppReady()) {
      if (datadir.exists() && !datadir.canRead()) {
        log.error("datadir is not readable. Aborting startup");
        showError(getString(R.string.start_error_datadir_unreadable), true, true);
      } else if (datadir.exists() && !datadir.isDirectory()) {
        log.error("datadir is not a directory. Aborting startup");
        showError(getString(R.string.start_error_datadir_not_directory), true, true);
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
          // this is a bit tricky: for compatibility reasons the actual content of the seed file
          // is the hexadecimal representation of the seed and not the seed itself
          final byte[] hexbytes = WalletUtils.readSeedFile(datadir, password);
          final byte[] bytes = Hex.decode(hexbytes);
          final ByteVector seed = ByteVector$.MODULE$.apply(bytes);
          final DeterministicWallet.ExtendedPrivateKey pk = DeterministicWallet.derivePrivateKey(
            DeterministicWallet.generate(seed), LocalKeyManager.nodeKeyBasePath(WalletUtils.getChainHash()));
          app.pin.set(password);
          app.seedHash.set(pk.privateKey().publicKey().hash160().toHex());
          app.backupKey_v1.set(EncryptedBackup.generateBackupKey_v1(pk));
          app.backupKey_v2.set(EncryptedBackup.generateBackupKey_v2(pk));

          // stop if we need to restore channels backup
          if (shouldRestoreChannelsBackup(prefs)) {
            startActivity(new Intent(getBaseContext(), RestoreChannelsBackupActivity.class));
            return;
          }
          // stop if no access to local storage for local backup
          if (!BackupUtils.Local.hasLocalAccess(getApplicationContext())) {
            final Intent backupSetupIntent = new Intent(getBaseContext(), SetupChannelsBackupActivity.class);
            if (prefs.getBoolean(Constants.SETTING_HAS_STARTED_ONCE, false)) {
              backupSetupIntent.putExtra(SetupChannelsBackupActivity.EXTRA_SETUP_IGNORE_GDRIVE_BACKUP, true);
            }
            startActivity(backupSetupIntent);
            return;
          }

          runOnUiThread(() -> mBinding.startupLog.setText(getString(R.string.start_log_seed_ok)));
          isStartingNode = true;
          new StartupTask().execute(app, seed);

        } catch (GeneralSecurityException e) {
          clearApp();
          isStartingNode = false;
          runOnUiThread(() -> {
            showError(getString(R.string.start_error_wrong_password));
            new Handler().postDelayed(() -> startNode(datadir, prefs), 1400);
          });
        } catch (Throwable t) {
          log.error("seed is unreadable", t);
          clearApp();
          isStartingNode = false;
          runOnUiThread(() -> showError(getString(R.string.start_error_unreadable_seed), true, true));
        }
      }
    }.start();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void processStartupFinish(StartupCompleteEvent event) {
    final File datadir = new File(app.getFilesDir(), Constants.ECLAIR_DATADIR);
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    switch (event.status) {
      case StartupTask.SUCCESS:
        if (isAppReady()) {
          app.scheduleExchangeRatePoll();
          prefs.edit()
            .putBoolean(Constants.SETTING_HAS_STARTED_ONCE, true)
            .putLong(Constants.SETTING_LAST_SUCCESSFUL_BOOT_DATE, System.currentTimeMillis())
            .apply();
          NetworkSyncWorker.scheduleSync();
          CheckElectrumWorker.schedule();
          goToHome();
        } else {
          // empty appkit, something went wrong.
          showError(getString(R.string.start_error_improper));
          new Handler().postDelayed(() -> startNode(datadir, prefs), 1400);
        }
        break;
      case StartupTask.NETWORK_ERROR:
        mBinding.startupLog.setText("");
        clearApp();
        showError(getString(R.string.start_error_connectivity), true, false);
        break;
      case StartupTask.TIMEOUT_ERROR:
        mBinding.startupLog.setText("");
        clearApp();
        showError(getString(R.string.start_error_timeout), true, true);
        break;
      default:
        mBinding.startupLog.setText("");
        clearApp();
        showError(getString(R.string.start_error_generic), true, true);
        break;
    }
    isStartingNode = false;
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void processStartupProgress(StartupProgressEvent event) {
    mBinding.startupLog.setText(event.message);
  }

  public void pickImportExistingWallet(View view) {
    Intent intent = new Intent(getBaseContext(), RestoreSeedActivity.class);
    startActivity(intent);
  }

  public void pickCreateNewWallet(View view) {
    Intent intent = new Intent(getBaseContext(), CreateSeedActivity.class);
    startActivity(intent);
  }

  public void openFAQ(View view) {
    Intent faqIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ACINQ/eclair-wallet/wiki/FAQ"));
    startActivity(faqIntent);
  }

  public void restart(View view) {
    restart();
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

    private final Logger log = LoggerFactory.getLogger(StartupTask.class);

    private final static int SUCCESS = 0;
    private final static int NETWORK_ERROR = 2;
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
        final ByteVector seed = (ByteVector) params[1];
        final File datadir = new File(app.getFilesDir(), Constants.ECLAIR_DATADIR);

        publishProgress(app.getString(R.string.start_log_init));
        // bootstrap hangs if network is unavailable.
        // TODO: The app should be able to handle an offline mode. Remove await from bootstrap and resolve appkit asynchronously.
        // AppKit should be available from the app without fully bootstrapping the node.
        final ConnectivityManager cm = (ConnectivityManager) app.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork == null || !activeNetwork.isConnectedOrConnecting()) {
          throw new EclairException.NetworkException();
        }

        app.checkupInit();
        cancelBackgroundWorks();

        publishProgress(app.getString(R.string.start_log_setting_up));
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app.getBaseContext());
        Class.forName("org.sqlite.JDBC");
        final Setup setup = new Setup(datadir, WalletUtils.getOverrideConfig(prefs), Option.apply(seed), app.system);
        setup.nodeParams().peersDb().addOrUpdatePeer(Constants.ACINQ_NODE_URI.nodeId(),
          NodeAddress$.MODULE$.fromParts(Constants.ACINQ_NODE_URI.address().getHost(), Constants.ACINQ_NODE_URI.address().getPort()).get());

        // ui refresh schedulers
        final ActorRef paymentsRefreshScheduler = app.system.actorOf(Props.create(RefreshScheduler.PaymentsRefreshScheduler.class), "PaymentsRefreshScheduler");
        final ActorRef channelsRefreshScheduler = app.system.actorOf(Props.create(RefreshScheduler.ChannelsRefreshScheduler.class), "ChannelsRefreshScheduler");
        final ActorRef balanceRefreshScheduler = app.system.actorOf(Props.create(RefreshScheduler.BalanceRefreshScheduler.class), "BalanceRefreshScheduler");

        // gui updater actor
        final ActorRef nodeSupervisor = app.system.actorOf(Props.create(NodeSupervisor.class, app.getDBHelper(),
          app.seedHash.get(), app.backupKey_v2.get(), paymentsRefreshScheduler, channelsRefreshScheduler, balanceRefreshScheduler), "NodeSupervisor");
        app.system.eventStream().subscribe(nodeSupervisor, ChannelEvent.class);
        app.system.eventStream().subscribe(nodeSupervisor, SyncProgress.class);
        app.system.eventStream().subscribe(nodeSupervisor, PaymentEvent.class);
        app.system.eventStream().subscribe(nodeSupervisor, PaymentLifecycle.PaymentResult.class);

        // electrum payment supervisor actor
        app.system.actorOf(Props.create(ElectrumSupervisor.class, app.getDBHelper(), paymentsRefreshScheduler, balanceRefreshScheduler), "ElectrumSupervisor");

        publishProgress(app.getString(R.string.start_log_starting_core));
        Future<Kit> fKit = setup.bootstrap();
        Kit kit = Await.result(fKit, Duration.create(60, "seconds"));
        ElectrumEclairWallet electrumWallet = (ElectrumEclairWallet) kit.wallet();

        app.appKit = new App.AppKit(electrumWallet, kit);
        app.monitorConnectivity();
        publishProgress(app.getString(R.string.start_log_done));
        return SUCCESS;

      } catch (EclairException.NetworkException t) {
        return NETWORK_ERROR;
      } catch (Throwable t) {
        log.error("failed to start eclair", t);
        if (t instanceof TimeoutException) {
          return TIMEOUT_ERROR;
        } else {
          return GENERIC_ERROR;
        }
      }
    }

    @Override
    protected void onPostExecute(Integer status) {
      EventBus.getDefault().post(new StartupCompleteEvent(status));
    }

    private void cancelBackgroundWorks() {
      final WorkManager workManager = WorkManager.getInstance();
      try {
        final List<WorkInfo> works = workManager.getWorkInfosByTag(NetworkSyncWorker.NETWORK_SYNC_TAG).get();
        works.addAll(workManager.getWorkInfosByTag(CheckElectrumWorker.ELECTRUM_CHECK_WORKER_TAG).get());
        if (works.isEmpty()) {
          log.info("no background works were found");
        } else {
          for (WorkInfo work : works) {
            log.info("found a background work in state {}, full data={}", work.getState(), work);
            workManager.cancelWorkById(work.getId()).getResult().get();
            log.info("successfully cancelled work {}", work);
          }
        }
      } catch (Exception e) {
        log.error("failed to retrieve or cancel background works", e);
        throw new RuntimeException("could not cancel background work");
      }
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
