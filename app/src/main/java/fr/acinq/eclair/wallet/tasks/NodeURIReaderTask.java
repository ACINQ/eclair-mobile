/*
 * Copyright 2018 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.eclair.wallet.tasks;

import android.os.AsyncTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.acinq.eclair.io.NodeURI;

public class NodeURIReaderTask extends AsyncTask<String, Integer, NodeURI> {

  private final Logger log = LoggerFactory.getLogger(NodeURIReaderTask.class);
  private final String nodeURIAsString;
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
      log.debug("could not read uri {} with cause {}", nodeURIAsString, t.getMessage());
    }
    return uri;
  }

  protected void onPostExecute(final NodeURI result) {
    delegate.processNodeURIFinish(result);
  }

  public interface AsyncNodeURIReaderTaskResponse {
    void processNodeURIFinish(final NodeURI output);
  }
}
