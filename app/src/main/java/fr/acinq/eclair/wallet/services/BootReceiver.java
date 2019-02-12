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
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.work.*;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * This receiver schedules a unique periodic worker synchronizing the Lightning Network routing table database.
 * <p>
 * This receiver is started when the device has booted. The work is also scheduled when the app starts, though being
 * unique, with the KEEP policy, the work will not be rescheduled if it already was scheduled.
 */
public class BootReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(final Context context, final Intent intent) {
    if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
      NetworkSyncWorker.scheduleSync();
      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
      if (prefs.getBoolean(Constants.SETTING_ENABLE_LIGHTNING_INBOUND_PAYMENTS, false)) {
        CheckElectrumWorker.scheduleASAP();
      }
    }
  }
}
