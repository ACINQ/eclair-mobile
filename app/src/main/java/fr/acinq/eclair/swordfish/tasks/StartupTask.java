package fr.acinq.eclair.swordfish.tasks;

import android.content.Context;
import android.os.AsyncTask;

import fr.acinq.eclair.swordfish.EclairHelper;

public class StartupTask extends AsyncTask<String, Integer, Long> {

  private AsyncSetupResponse delegate;
  private Context context;
  public StartupTask(AsyncSetupResponse delegate, Context context) {
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

  public interface AsyncSetupResponse {
    void processFinish(String output);
  }

}
