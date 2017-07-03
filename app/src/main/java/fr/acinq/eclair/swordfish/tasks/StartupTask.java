package fr.acinq.eclair.swordfish.tasks;

import android.os.AsyncTask;

import java.io.File;

import fr.acinq.eclair.swordfish.EclairHelper;

public class StartupTask extends AsyncTask<String, Integer, Long> {

  public interface AsyncSetupResponse {
    void processFinish(String output);
  }
  private AsyncSetupResponse delegate;
  private File appFileDir;

  public StartupTask(AsyncSetupResponse delegate, File appFileDir){
    this.delegate = delegate;
    this.appFileDir = appFileDir;
  }

  @Override
  protected Long doInBackground(String... params) {
    EclairHelper.getInstance(appFileDir);
    return 1L;
  }

  protected void onPostExecute(Long result) {
    delegate.processFinish("done");
  }

}
