package fr.acinq.eclair.swordfish;

import android.content.Context;
import android.util.Log;

import java.io.File;

import fr.acinq.eclair.Setup;

/**
 * Created by Dominique on 24/05/2017.
 */

public class EclairSetup {
  private static EclairSetup mInstance = null;

  private Setup s;
  private EclairSetup(Context context) {
    File data = new File(context.getFilesDir(), "eclair-wallet-data");
    Log.i("launcher", "Data dir exists ? " + (new File(data, "seed.dat")).exists());
    for (String f : (new File(data, "db")).list()) {
      Log.i("launcher", "File in db dir : " + f);
    }
    Setup s = new Setup(data, "system");
    s.boostrap();
  }

  public static EclairSetup getInstance(Context context) {
    if (mInstance == null) {
      Class clazz = EclairSetup.class;
      synchronized (clazz) {
        mInstance = new EclairSetup(context);
      }
    }
    return mInstance;
  }

  public Setup getSetup() {
    return s;
  }
}
