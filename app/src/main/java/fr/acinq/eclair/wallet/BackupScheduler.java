package fr.acinq.eclair.wallet;

import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.util.concurrent.TimeUnit;

import akka.actor.UntypedActor;
import akka.japi.Procedure;
import fr.acinq.eclair.wallet.events.BackupEclairDBEvent;
import scala.concurrent.duration.Duration;

public class BackupScheduler extends UntypedActor {
  private static final String TAG = "BackupScheduler";
  public final static String DO_BACKUP = "dobackup";
  public final static String WAKE_UP = "wakeup";

  public void onReceive(Object message) {
    if (message.equals(DO_BACKUP)) {
      Log.i(TAG, "scheduling backup");
      // before going to sleep, we set up the alarm
      context().system().scheduler().scheduleOnce(Duration.create(1, TimeUnit.SECONDS), self(), WAKE_UP, context().dispatcher(), self());
      getContext().become(sleep);
    }
  }

  Procedure<Object> sleep = message -> {
    if (message.equals(DO_BACKUP)) {
      Log.i(TAG, "ignoring backup request (already scheduled)");
      // ignored, we will do the backup when alarm rings
    } else if (message.equals(WAKE_UP)) {
      Log.i(TAG, "dispatching backup request");
      EventBus.getDefault().post(new BackupEclairDBEvent());
      getContext().unbecome();
    }
  };
}
