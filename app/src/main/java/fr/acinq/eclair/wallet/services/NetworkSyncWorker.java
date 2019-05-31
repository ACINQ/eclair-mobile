/*
 * Copyright 2019 ACINQ SAS
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
import androidx.work.*;
import com.typesafe.config.ConfigFactory;
import fr.acinq.eclair.SyncLiteSetup;
import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Option;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * This worker starts a light instance of the node tasked with synchronizing the Lightning Network routing table
 * database, i.e. fetching the latest network changes from a remote node in the network and updating the local database.
 * <p>
 * This sync will happen in the background, even when the app is not started. When the work is finished, the node instance
 * is shutdown.
 */
public class NetworkSyncWorker extends Worker {
  private final static Logger log = LoggerFactory.getLogger(NetworkSyncWorker.class);
  private final ActorSystem system = ActorSystem.apply("sync-system");
  private SyncLiteSetup liteSetup;

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
    if (liteSetup != null && liteSetup.nodeParams() != null) {
      try {
        liteSetup.nodeParams().db().channels().close(); // eclair.sqlite
        liteSetup.nodeParams().db().network().close(); // network.sqlite
        liteSetup.nodeParams().db().audit().close(); // audit.sqlite
      } catch (Throwable t) {
        log.error("could not close at least one database connection opened by litesetup", t);
      }
    }
  }

  @NonNull
  @Override
  public Result doWork() {
    log.info("NetworkSyncWorker has started");
    final Context context = getApplicationContext();

    if (!WalletUtils.getEclairDBFile(context).exists()) {
      log.info("no eclair db file yet, aborting...");
      return Result.success();
    }

    if (((App) context).appKit != null) {
      log.info("application is running (appkit not null)");
      return Result.success();
    } else {
      try {
        Class.forName("org.sqlite.JDBC");
        liteSetup = new SyncLiteSetup(new File(context.getFilesDir(), Constants.ECLAIR_DATADIR), ConfigFactory.empty(), Constants.ACINQ_NODE_URI, Option.empty(), system);
        Await.result(liteSetup.sync(), Duration.Inf());
        log.info("sync has completed");
        return Result.success();
      } catch (Exception e) {
        log.error("network sync worker failed: ", e);
        return Result.failure();
      } finally {
        cleanup();
      }
    }
  }

  public static final String NETWORK_SYNC_TAG = BuildConfig.APPLICATION_ID + ".PeriodicNetworkSyncWork";

  public static void scheduleSync() {
    log.info("scheduling sync work");
    // flex adds a pause between each sync work to make sure that a sync work is not run immediately after the previous one (regardless of interval)
    final PeriodicWorkRequest.Builder syncWork = new PeriodicWorkRequest.Builder(NetworkSyncWorker.class, 12, TimeUnit.HOURS, 8, TimeUnit.HOURS)
      .addTag(NETWORK_SYNC_TAG)
      .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build());
    WorkManager.getInstance().enqueueUniquePeriodicWork(NETWORK_SYNC_TAG, ExistingPeriodicWorkPolicy.REPLACE, syncWork.build());
  }

  public static void doSyncASAP() {
    final OneTimeWorkRequest syncWork = new OneTimeWorkRequest.Builder(NetworkSyncWorker.class)
      .addTag(NETWORK_SYNC_TAG)
      .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
      .build();
    WorkManager.getInstance().enqueueUniqueWork(NETWORK_SYNC_TAG, ExistingWorkPolicy.REPLACE, syncWork);
  }
}
