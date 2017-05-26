package fr.acinq.eclair.swordfish;

import android.content.Context;
import android.os.AsyncTask;

/**
 * Created by Dominique on 26/05/2017.
 */

public class StartupTask extends AsyncTask<String, Integer, Long> {

  public interface AsyncSetupResponse {
    void processFinish(String output);
  }
  private AsyncSetupResponse delegate;
  private Context context;

  public StartupTask(AsyncSetupResponse delegate, Context context){
    this.delegate = delegate;
    this.context = context;
  }

  @Override
  protected Long doInBackground(String... params) {
    EclairHelper.getInstance(context);
    return 1L;
  }

  protected void onPostExecute(Long result) {
    delegate.processFinish("done");
  }

}
