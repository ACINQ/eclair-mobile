package fr.acinq.eclair.wallet.tasks;

import android.os.AsyncTask;
import android.util.Log;

import fr.acinq.eclair.io.NodeURI;

public class NodeURIReaderTask extends AsyncTask<String, Integer, NodeURI> {

  private static final String TAG = "NodeURITask";
  private final String nodeURIAsString;
  private String errorMessage = null;
  private final AsyncNodeURIReaderTaskResponse delegate;

  public NodeURIReaderTask(AsyncNodeURIReaderTaskResponse delegate, String nodeURIAsString) {
    this.delegate = delegate;
    this.nodeURIAsString = nodeURIAsString;
  }

  @Override
  protected NodeURI doInBackground(String... params) {
    NodeURI uri = null;
    try {
      uri = NodeURI.parse(nodeURIAsString);
    } catch (Throwable t) {
      Log.d(TAG, "Could not read uri=" + nodeURIAsString + " with cause: " + t.getMessage());
      errorMessage = t.getMessage();
    }
    return uri;
  }

  protected void onPostExecute(final NodeURI result) {
    delegate.processNodeURIFinish(result, errorMessage);
  }

  public interface AsyncNodeURIReaderTaskResponse {
    void processNodeURIFinish(final NodeURI output, final String message);
  }
}
