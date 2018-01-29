package fr.acinq.eclair.wallet.activities;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.typesafe.config.ConfigFactory;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;

import akka.actor.ActorRef;
import akka.actor.Props;
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
import scala.Option;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

public class StartupActivity extends EclairActivity {

  private TextView statusTextView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_startup);
    statusTextView = findViewById(R.id.startup_status);
    if (app.appKit == null) {
      new StartupTask().execute(app);
    } else {
      goToHome();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this);
    }
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
      statusTextView.append("\n\nFailed to start eclair...");
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void processStartupProgress(StartupProgressEvent event) {
    statusTextView.setText(event.message);
  }

  private void goToHome() {
    Intent homeIntent = new Intent(getBaseContext(), HomeActivity.class);
    homeIntent.putExtra(HomeActivity.EXTRA_PAYMENT_URI, getIntent().getData());
    startActivity(homeIntent);
  }

  /**
   * Starts the eclair node in an asynchronous task.
   * When the task is finished, executes `processStartupFinish` in StartupActivity.
   */
  private static class StartupTask extends AsyncTask<App, String, String> {
    private static final String TAG = "StartupTask";

    @Override
    protected void onProgressUpdate(String... status) {
      super.onProgressUpdate(status);
      EventBus.getDefault().post(new StartupProgressEvent(status[0]));
    }

    @Override
    protected String doInBackground(App... params) {
      try {
        App app = params[0];
        publishProgress("setting up DB");
        app.checkupInit();
        publishProgress("initializing system");
        final File datadir = new File(app.getFilesDir(), App.DATADIR_NAME);
        Log.d(TAG, "Accessing Eclair Setup with datadir " + datadir.getAbsolutePath());

        Class.forName("org.sqlite.JDBC");
        publishProgress("setting up eclair");
        Setup setup = new Setup(datadir, Option.apply((EclairWallet) null), ConfigFactory.empty(), app.system);

        // gui and electrum supervisor actors
        ActorRef guiUpdater = app.system.actorOf(Props.create(EclairEventService.class, app.getDBHelper()));
        setup.system().eventStream().subscribe(guiUpdater, ChannelEvent.class);
        setup.system().eventStream().subscribe(guiUpdater, PaymentEvent.class);
        setup.system().eventStream().subscribe(guiUpdater, NetworkEvent.class);
        app.system.actorOf(Props.create(PaymentSupervisor.class, app.getDBHelper()), "payments");

        publishProgress("starting core");
        // starting eclair
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
