package fr.acinq.eclair.wallet.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.ViewStub;

import com.typesafe.config.ConfigFactory;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.util.List;

import akka.actor.ActorRef;
import akka.actor.Props;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.MnemonicCode;
import fr.acinq.eclair.DBCompatChecker;
import fr.acinq.eclair.Kit;
import fr.acinq.eclair.Setup;
import fr.acinq.eclair.blockchain.EclairWallet;
import fr.acinq.eclair.blockchain.electrum.ElectrumEclairWallet;
import fr.acinq.eclair.channel.ChannelEvent;
import fr.acinq.eclair.payment.PaymentEvent;
import fr.acinq.eclair.router.NetworkEvent;
import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.EclairEventService;
import fr.acinq.eclair.wallet.PaymentSupervisor;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityStartupBinding;
import fr.acinq.eclair.wallet.databinding.StubUsageDisclaimerBinding;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import scala.Option;
import scala.collection.JavaConverters;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

public class StartupActivity extends EclairActivity {

  private static final String TAG = "StartupActivity";
  private ActivityStartupBinding mBinding;
  private StubUsageDisclaimerBinding mDisclaimerBinding;

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
    if (app.appKit != null) {
      goToHome();
    } else {
      showError("Failed to start eclair...");
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void processStartupProgress(StartupProgressEvent event) {
    mBinding.startupLog.setText(event.message);
  }

  private void showError(final String message) {
    mBinding.startupError.setVisibility(View.VISIBLE);
    mBinding.startupError.setText(message);
  }

  private void goToHome() {
    Intent homeIntent = new Intent(getBaseContext(), HomeActivity.class);
    homeIntent.putExtra(HomeActivity.EXTRA_PAYMENT_URI, getIntent().getData());
    startActivity(homeIntent);
  }

  public void closeApp(View view) {
    finishAndRemoveTask();
    finishAffinity();
  }

  private void startCheckup(final File datadir, final SharedPreferences prefs) {
    if (prefs.getBoolean(Constants.SETTING_SHOW_DISCLAIMER, true)) {
      mBinding.stubDisclaimer.getViewStub().inflate();
      mDisclaimerBinding.disclaimerFinish.setOnClickListener(v -> {
        mDisclaimerBinding.getRoot().setVisibility(View.GONE);
        prefs.edit().putBoolean(Constants.SETTING_SHOW_DISCLAIMER, false).apply();
        startNodeChecker(datadir, prefs);
      });
      mDisclaimerBinding.disclaimerText.setText(Html.fromHtml(getString(R.string.disclaimer_1, getString(R.string.chain_name))));
    } else {
      startNodeChecker(datadir, prefs);
    }
  }

  private void startNodeChecker(final File datadir, final SharedPreferences prefs) {
    if (!datadir.exists()) {
      if (!mBinding.stubPickInitWallet.isInflated()) {
        mBinding.stubPickInitWallet.getViewStub().inflate();
      }
    } else {
      startNode(datadir);
    }
  }

  /**
   * Starts up the eclair node if needed
   */
  private void startNode(final File datadir) {
    if (mBinding.stubDisclaimer.isInflated()) {
      mBinding.stubDisclaimer.getRoot().setVisibility(View.GONE);
    }
    if (mBinding.stubPickInitWallet.isInflated()) {
      mBinding.stubPickInitWallet.getRoot().setVisibility(View.GONE);
    }

    if (app.appKit == null) {
      // core is not started, so starts it
      // first check datadir state
      if (datadir.exists() && !datadir.canRead()) {
        Log.e(TAG, "datadir is not readable. Aborting startup");
        showError("Datadir is not readable.");
      } else if (datadir.exists() && !datadir.isDirectory()) {
        Log.e(TAG, "datadir is not a directory. Aborting startup");
        showError("Datadir is not a directory.");
      } else {
        try {
          final List<String> words = WalletUtils.readMnemonicsFile(datadir);
          if (words == null || words.size() == 0) {
            Log.e(TAG, "seed mnemonics is empty, can not start");
            showError("Can not start eclair with empty seed");
          } else {
            // using a empty passphrase
            new StartupTask(MnemonicCode.toSeed(JavaConverters.collectionAsScalaIterableConverter(words).asScala().toSeq(), "")).execute(app);
          }
        } catch (IOException e) {
          showError("Seed is unreadable. Aborting.");
        }
      }
    } else {
      // core is started, go to home and use it
      goToHome();
    }
  }

  public void pickImportExistingWallet(View view) {
    Intent intent = new Intent(getBaseContext(), ImportWalletActivity.class);
    startActivity(intent);
  }

  public void pickCreateNewWallet(View view) {
    Intent intent = new Intent(getBaseContext(), CreateWalletRecoveryActivity.class);
    startActivity(intent);
  }

  /**
   * Starts the eclair node in an asynchronous task.
   * When the task is finished, executes `processStartupFinish` in StartupActivity.
   */
  private static class StartupTask extends AsyncTask<App, String, String> {
    private static final String TAG = "StartupTask";

    private final BinaryData seed;

    private StartupTask(BinaryData seed) {
      this.seed = seed;
    }

    @Override
    protected void onProgressUpdate(String... status) {
      super.onProgressUpdate(status);
      EventBus.getDefault().post(new StartupProgressEvent(status[0]));
    }

    @Override
    protected String doInBackground(App... params) {
      try {
        App app = params[0];
        publishProgress("initializing system");
        app.checkupInit();
        final File datadir = new File(app.getFilesDir(), Constants.ECLAIR_DATADIR);
        Log.d(TAG, "Accessing Eclair Setup with datadir " + datadir.getAbsolutePath());

        Class.forName("org.sqlite.JDBC");
        publishProgress("setting up eclair");
        Setup setup = new Setup(datadir, Option.apply(null), ConfigFactory.empty(), app.system, Option.apply(seed));

        // gui and electrum supervisor actors
        ActorRef guiUpdater = app.system.actorOf(Props.create(EclairEventService.class, app.getDBHelper()));
        setup.system().eventStream().subscribe(guiUpdater, ChannelEvent.class);
        setup.system().eventStream().subscribe(guiUpdater, PaymentEvent.class);
        setup.system().eventStream().subscribe(guiUpdater, NetworkEvent.class);
        app.system.actorOf(Props.create(PaymentSupervisor.class, app.getDBHelper()), "payments");

        publishProgress("starting core");
        Future<Kit> fKit = setup.bootstrap();
        Kit kit = Await.result(fKit, Duration.create(20, "seconds"));
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
        return "done";
      } catch (Exception e) {
        Log.e(TAG, "Failed to start eclair", e);
        return null;
      }
    }

    @Override
    protected void onPostExecute(String message) {
      EventBus.getDefault().post(new StartupCompleteEvent());
    }
  }

  public static class StartupCompleteEvent {
  }

  public static class StartupProgressEvent {
    final String message;

    public StartupProgressEvent(String message) {
      this.message = message;
    }
  }
}
