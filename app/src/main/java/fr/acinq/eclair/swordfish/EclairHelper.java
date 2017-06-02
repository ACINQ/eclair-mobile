package fr.acinq.eclair.swordfish;

import android.content.Context;

import java.io.File;

import akka.actor.ActorRef;
import akka.actor.Props;
import fr.acinq.eclair.Setup;
import fr.acinq.eclair.channel.ChannelEvent;

/**
 * Created by Dominique on 24/05/2017.
 */

public class EclairHelper {
  private static EclairHelper mInstance = null;

  private Setup setup;
  private ActorRef guiUpdater;

  private EclairHelper() {}

  private EclairHelper(Context context) {
    File data = new File(context.getFilesDir(), "eclair-wallet-data");
    System.setProperty("eclair.node-alias", "swordfish");
    Setup setup = new Setup(data, "system");
    this.setup = setup;

    this.guiUpdater = this.setup.system().actorOf(Props.create(EclairEventService.class));
    setup.system().eventStream().subscribe(guiUpdater, ChannelEvent.class);
  }

  public static boolean hasInstance() {
    return mInstance == null;
  }

  public static EclairHelper getInstance(Context context) {
    if (mInstance == null) {
      Class clazz = EclairHelper.class;
      synchronized (clazz) {
        mInstance = new EclairHelper(context);
      }
    }
    return mInstance;
  }

  public Setup getSetup() {
    return this.setup;
  }
}
