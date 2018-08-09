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

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.JobIntentService;
import android.util.Log;
import android.widget.Toast;

import fr.acinq.eclair.wallet.utils.Constants;

public class TerminationService extends Service {

  private static final String TAG = TerminationService.class.getSimpleName();
  private String seedHash;


  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    seedHash = intent.getStringExtra(ChannelsBackupService.SEED_HASH_EXTRA);
    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy() {
    Log.w(TAG, "DESTROY");
    super.onDestroy();
  }

  @Override
  public void onTaskRemoved(Intent rootIntent) {
    Toast.makeText(this, "TERMINATED", Toast.LENGTH_SHORT).show();
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    if (prefs.getBoolean(Constants.SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_ENABLED, false)) {
      final Intent backupIntent = new Intent();
      backupIntent.putExtra(ChannelsBackupService.SEED_HASH_EXTRA, seedHash);
      JobIntentService.enqueueWork(getApplicationContext(), ChannelsBackupService.class, 1, backupIntent);
    }
    stopSelf();
  }
}
