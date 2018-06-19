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

package fr.acinq.eclair.wallet.utils;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.activities.StartupActivity;

public class StartNotificationReminder extends BroadcastReceiver {

  private static final String TAG = StartNotificationReminder.class.getSimpleName();

  public static PendingIntent getBroadcastIntent(Context context) {
    return PendingIntent.getBroadcast(context, Constants.NOTIF_START_REMINDER_REQUEST_CODE,
      new Intent(context, StartNotificationReminder.class), 0);
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    final long start = System.currentTimeMillis() + 10 * 1000; // 5 sec leeway
    final long nextReminder = PreferenceManager.getDefaultSharedPreferences(context).getLong(Constants.SETTING_NEXT_START_REMINDER_ALARM, 0);
    if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && nextReminder == 0) {
      // do nothing: app has never been started with the ln receiving feature and no notification is needed
    } else if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && nextReminder > start) {
      // reminder date is far in the future, schedule an alarm manager
      final AlarmManager m = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
      if (m != null) m.setRepeating(AlarmManager.RTC_WAKEUP, nextReminder, Constants.ONE_DAY_MS, getBroadcastIntent(context));
    } else {
      final Intent startIntent = new Intent(context, StartupActivity.class);
      startIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
      final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Constants.NOTIF_CHANNEL_START_REMINDER_ID)
        .setSmallIcon(R.drawable.eclair_256x256)
        .setContentTitle(context.getString(R.string.notif_startreminder_title))
        .setContentText(context.getString(R.string.notif_startreminder_message))
        .setContentIntent(PendingIntent.getActivity(context, Constants.NOTIF_START_REMINDER_REQUEST_CODE, startIntent, PendingIntent.FLAG_UPDATE_CURRENT))
        .setAutoCancel(true);
      NotificationManagerCompat.from(context).notify(Constants.NOTIF_START_REMINDER_REQUEST_CODE, builder.build());
    }
  }
}
