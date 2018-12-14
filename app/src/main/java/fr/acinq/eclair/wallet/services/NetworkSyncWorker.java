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

package fr.acinq.eclair.wallet.services;

import akka.actor.ActorSystem;
import android.content.Context;
import android.support.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.typesafe.config.ConfigFactory;
import fr.acinq.eclair.SyncLiteSetup;
import fr.acinq.eclair.io.NodeURI;
import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.io.File;

/**
 * This worker starts a light instance of the node tasked with synchronizing the Lightning Network routing table
 * database, i.e. fetching the latest network changes from a remote node in the network and updating the local database.
 * <p>
 * This sync will happen in the background, even when the app is not started. When the work is finished, the node instance
 * is shutdown.
 */
public class NetworkSyncWorker extends Worker {
  private final Logger log = LoggerFactory.getLogger(NetworkSyncWorker.class);
  private final ActorSystem system = ActorSystem.apply("sync-system");

  public NetworkSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
    super(context, workerParams);
  }

  @Override
  public void onStopped() {
    super.onStopped();
    cleanup();
  }

  private void cleanup() {
    if (!system.isTerminated()) {
      system.shutdown();
      log.info("system shutdown requested...");
      system.awaitTermination();
      log.info("termination completed");
    }
  }

  @NonNull
  @Override
  public Result doWork() {
    log.info("NetworkSyncWorker has started");
    final Context context = getApplicationContext();
    if (((App) context).appKit != null) {
      log.info("application is running (appkit not null)");
      return Result.FAILURE;
    } else {
      try {
        final SyncLiteSetup setup = new SyncLiteSetup(new File(context.getFilesDir(), Constants.ECLAIR_DATADIR), ConfigFactory.empty(), NodeURI.parse(WalletUtils.ACINQ_NODE), system);
        Await.result(setup.sync(), Duration.Inf());
        log.info("sync has completed");
        return Result.SUCCESS;
      } catch (Exception e) {
        log.error("network sync worker failed: ", e);
        return Result.FAILURE;
      } finally {
        cleanup();
      }
    }
  }
}
