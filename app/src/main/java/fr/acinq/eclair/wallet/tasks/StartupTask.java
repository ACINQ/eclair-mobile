package fr.acinq.eclair.wallet.tasks;

import android.content.Context;
import android.os.AsyncTask;

import fr.acinq.eclair.wallet.EclairHelper;
import fr.acinq.eclair.wallet.EclairStartException;

public class StartupTask extends AsyncTask<String, Integer, EclairHelper> {

  private AsyncSetupResponse delegate;
  private Context context;
  public StartupTask(AsyncSetupResponse delegate, Context context) {
    this.delegate = delegate;
    this.context = context;
  }

  @Override
  protected EclairHelper doInBackground(String... params) {
    try {
      return new EclairHelper(context);
    } catch (EclairStartException e) {
      return null;
    }
  }

  @Override
  protected void onPostExecute(EclairHelper instance) {
    delegate.processFinish(instance);
  }

  public interface AsyncSetupResponse {
    void processFinish(EclairHelper instance);
  }

}
