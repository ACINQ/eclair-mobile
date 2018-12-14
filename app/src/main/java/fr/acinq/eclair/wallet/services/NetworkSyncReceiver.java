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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.work.*;
import fr.acinq.eclair.wallet.BuildConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * This receiver schedules a unique periodic worker synchronizing the Lightning Network routing table database.
 * <p>
 * This receiver is started when the device has booted. The work is also scheduled when the app starts, though being
 * unique, with the KEEP policy, the work will not be rescheduled if it already was scheduled.
 */
public class NetworkSyncReceiver extends BroadcastReceiver {

  private static final Logger log = LoggerFactory.getLogger(NetworkSyncReceiver.class);
  public static final String NETWORK_SYNC_TAG = BuildConfig.APPLICATION_ID + ".PeriodicNetworkSyncWork";

  @Override
  public void onReceive(final Context context, final Intent intent) {
    if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
      scheduleSync();
    }
  }

  public static void scheduleSync() {
    log.info("scheduling sync work");
    // flex adds a pause between each sync work to make sure that a sync work is not run immediately after the previous one (regardless of interval)
    final PeriodicWorkRequest.Builder syncWork = new PeriodicWorkRequest.Builder(NetworkSyncWorker.class, 6, TimeUnit.HOURS, 4, TimeUnit.HOURS)
      .addTag(NETWORK_SYNC_TAG)
      .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build());
    WorkManager.getInstance().enqueueUniquePeriodicWork(NETWORK_SYNC_TAG, ExistingPeriodicWorkPolicy.KEEP, syncWork.build());
  }
}
