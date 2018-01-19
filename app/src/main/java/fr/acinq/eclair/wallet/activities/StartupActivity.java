package fr.acinq.eclair.wallet.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.typesafe.config.ConfigFactory;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.DBCompatChecker;
import fr.acinq.eclair.Kit;
import fr.acinq.eclair.Setup;
import fr.acinq.eclair.blockchain.EclairWallet;
import fr.acinq.eclair.blockchain.electrum.ElectrumEclairWallet;
import fr.acinq.eclair.channel.ChannelEvent;
import fr.acinq.eclair.payment.PaymentEvent;
import fr.acinq.eclair.router.NetworkEvent;
import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.DBHelper;
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
      new StartupTask().execute(getApplicationContext());
    } else {
      goToHome();
    }
  }

  public void processStartupFinish(App.AppKit output) {
    if (output != null) {
      app.appKit = output;
      goToHome();
    } else {
      statusTextView.append("\n\nFailed to start eclair...");
    }
  }

  private void goToHome() {
    startActivity(new Intent(getBaseContext(), HomeActivity.class));
  }

  private class StartupTask extends AsyncTask<Context, String, App.AppKit> {
    private static final String TAG = "StartupTask";

    @Override
    protected void onProgressUpdate(String... status) {
      super.onProgressUpdate(status);
      statusTextView.setText(status[0]);
    }

    @Override
    protected App.AppKit doInBackground(Context... params) {
      try {
        publishProgress("setting up DB");
        app.checkupInit();
        publishProgress("initializing system");
        final File datadir = new File(params[0].getFilesDir(), App.DATADIR_NAME);
        Log.d(TAG, "Accessing Eclair Setup with datadir " + datadir.getAbsolutePath());

        Class.forName("org.sqlite.JDBC");
        publishProgress("setting up eclair");
        Setup setup = new Setup(datadir, Option.apply((EclairWallet) null), ConfigFactory.empty(), app.system);

        publishProgress("starting gui actor");
        // gui and electrum supervisor actors
        ActorRef guiUpdater = app.system.actorOf(Props.create(EclairEventService.class, app.getDBHelper()));
        setup.system().eventStream().subscribe(guiUpdater, ChannelEvent.class);
        setup.system().eventStream().subscribe(guiUpdater, PaymentEvent.class);
        setup.system().eventStream().subscribe(guiUpdater, NetworkEvent.class);
        publishProgress("starting onchain supervisor");
        app.system.actorOf(Props.create(PaymentSupervisor.class, app.getDBHelper()), "payments");

        publishProgress("starting core");
        // starting eclair
        Future<Kit> fKit = setup.bootstrap();
        Kit kit = Await.result(fKit, Duration.create(20, "seconds"));
        publishProgress("core successfully started");
        ElectrumEclairWallet electrumWallet = (ElectrumEclairWallet) kit.wallet();
        publishProgress("checking compatibility");
        boolean isDBCompatible = true;
        try {
          DBCompatChecker.checkDBCompatibility(setup.nodeParams());
        } catch (Exception e) {
          isDBCompatible = false;
        }
        publishProgress("done");
        return new App.AppKit(electrumWallet, kit, isDBCompatible);

      } catch (Exception e) {
        Log.e(TAG, "Failed to start eclair", e);
        return null;
      }
    }

    @Override
    protected void onPostExecute(final App.AppKit result) {
      processStartupFinish(result);
    }
  }
}
