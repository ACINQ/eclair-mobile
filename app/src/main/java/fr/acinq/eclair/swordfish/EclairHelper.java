package fr.acinq.eclair.swordfish;

import android.content.Context;
import android.util.Log;

import java.io.File;

import fr.acinq.eclair.Setup;

/**
 * Created by Dominique on 24/05/2017.
 */

public class EclairHelper {
  private static EclairHelper mInstance = null;

  private Setup setup;

  private EclairHelper() {}

  private EclairHelper(Context context) {
    File data = new File(context.getFilesDir(), "eclair-wallet-data");
    Setup s = new Setup(data, "system");
    s.boostrap();
    Log.i("launcher", "Data dir exists ? " + (new File(data, "seed.dat")).exists());
    for (String f : (new File(data, "db")).list()) {
      Log.i("launcher", "File in db dir : " + f);
    }
    this.setup = s;
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
