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
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.text.format.DateUtils;
import androidx.work.*;
import fr.acinq.eclair.CheckElectrumSetup;
import fr.acinq.eclair.WatchListener;
import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.activities.StartupActivity;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Option;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * This worker starts a light instance of the
 */
public class CheckElectrumWorker extends Worker {
  private static final Logger log = LoggerFactory.getLogger(CheckElectrumWorker.class);
  public static final String ELECTRUM_CHECK_WORKER_TAG = BuildConfig.APPLICATION_ID + ".CheckElectrumWorker";

  /**
   * Delay in milliseconds. If no electrum check has occurred since (now) - (this), we consider that the device is
   * blocking this application from working in background.
   * <p>
   * Should be high enough as to not trigger false positives.
   */
  public static final long DELAY_BEFORE_BACKGROUND_WARNING = DateUtils.DAY_IN_MILLIS * 5;

  /**
   * Delay in milliseconds in which the last electrum check can be considered fresh enough that users do not need
   * to be reminded that eclair needs a working connection.
   */
  private static final long MAX_FRESH_WINDOW = DateUtils.DAY_IN_MILLIS * 3;

  /**
   * Delay in milliseconds in which the last electrum check can be considered fresh enough that users do not need
   * to be reminded that eclair needs a working connection, IF this last check returned OK.
   */
  private static final long MAX_FRESH_WINDOW_IF_OK = DateUtils.DAY_IN_MILLIS * 5;

  private final ActorSystem system = ActorSystem.apply("check-electrum-system");
  private CheckElectrumSetup setup;

  public CheckElectrumWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
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
      log.debug("system shutdown requested...");
      system.awaitTermination();
      log.info("termination completed");
    }
    if (setup != null && setup.nodeParams() != null) {
      try {
        setup.nodeParams().db().channels().close(); // eclair.sqlite
        setup.nodeParams().db().network().close(); // network.sqlite
        setup.nodeParams().db().audit().close(); // audit.sqlite
      } catch (Throwable t) {
        log.error("could not close at least one database connection opened by check electrum setup", t);
      }
    }
  }

  @NonNull
  @Override
  public Result doWork() {
    final Context context = getApplicationContext();

    if (!WalletUtils.getEclairDBFile(context).exists()) {
      log.info("no eclair db file yet, aborting...");
      return Result.success();
    }

    log.info("worker has started");
    // -- if app is running in foreground, check is not possible
    if (((App) context).appKit != null) {
      log.info("application is already running (appkit not null), no need to check");
      timestampAttempt(context);
      return Result.failure();
    } else {
      try {
        final WatchListener.WatchResult result = startElectrumCheck(context);
        log.info("check has completed with result {}", result);
        if (result instanceof WatchListener.NotOk$) {
          log.warn("cheating attempt detected, app must be started ASAP!");
          showNotification(context, true);
        }
        saveLastCheckResult(context, result);
        return Result.success();
      } catch (Throwable t) {
        log.error("electrum check has failed: ", t);
        return Result.failure();
      } finally {
        timestampAttempt(context);
        cleanup();
      }
    }
  }

  /**
   * Saving the timestamp of the last electrum check attempts lets the app know if the worker is able to run in background.
   * See {@link App#detectBackgroundRunnable} method.
   */
  private void timestampAttempt(final Context context) {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.edit()
      .putLong(Constants.SETTING_ELECTRUM_CHECK_LAST_ATTEMPT_TIMESTAMP, System.currentTimeMillis())
      .apply();
  }

  private void saveLastCheckResult(@NonNull final Context context, final WatchListener.WatchResult result) {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.edit()
      .putLong(Constants.SETTING_ELECTRUM_CHECK_LAST_OUTCOME_TIMESTAMP, System.currentTimeMillis())
      .putString(Constants.SETTING_ELECTRUM_CHECK_LAST_OUTCOME_RESULT, result.toString())
      .apply();
  }

  private WatchListener.WatchResult startElectrumCheck(@NonNull final Context context) throws Exception {
    Class.forName("org.sqlite.JDBC");
    setup = new CheckElectrumSetup(new File(context.getFilesDir(), Constants.ECLAIR_DATADIR), WalletUtils.getOverrideConfig(PreferenceManager.getDefaultSharedPreferences(context)), Option.empty(), system);
    if (setup.nodeParams().db().channels().listLocalChannels().isEmpty()) {
      log.info("no active channels found");
      return WatchListener.Ok$.MODULE$;
    } else {
      // if there is no network connectivity, return failure
      final ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      final NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
      if (activeNetwork == null || !activeNetwork.isConnectedOrConnecting()) {
        log.info("we have no connection, let's check if the check was run recently");
        if (!isLastCheckFresh(context)) {
          log.warn("let's notify the user: we have not been able to check txs for a while");
          showNotification(context, false);
        }
        return WatchListener.Unknown$.MODULE$;
      } else {
        return Await.result(setup.check(), Duration.apply(3, TimeUnit.MINUTES));
      }
    }
  }

  private boolean isLastCheckFresh(@NonNull final Context context) {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    final long currentTime = System.currentTimeMillis();
    final long lastBootDate = prefs.getLong(Constants.SETTING_LAST_SUCCESSFUL_BOOT_DATE, 0);
    final long lastCheckDate = prefs.getLong(Constants.SETTING_ELECTRUM_CHECK_LAST_OUTCOME_TIMESTAMP, 0);
    final String lastCheckResult = prefs.getString(Constants.SETTING_ELECTRUM_CHECK_LAST_OUTCOME_RESULT, null);
    final long delaySinceCheck = currentTime - lastCheckDate;

    log.debug("last boot happened on {}, last check happened on {}, delay={} ms", DateFormat.getDateTimeInstance().format(new Date(lastBootDate)),
      DateFormat.getDateTimeInstance().format(new Date(lastCheckDate)), DateUtils.formatElapsedTime(delaySinceCheck / 1000));

    if (lastBootDate == 0) {
      log.warn("last boot date has never been set");
      return false;
    }

    if (currentTime - lastBootDate < MAX_FRESH_WINDOW_IF_OK) {
      // equivalent to a fresh check with OK result
      log.debug("fresh last boot");
      return true;
    }

    if (lastCheckDate == 0) {
      log.debug("check has never run");
      return false;
    }

    if (delaySinceCheck < MAX_FRESH_WINDOW) {
      return true;
    }

    if (delaySinceCheck < MAX_FRESH_WINDOW_IF_OK && WatchListener.Ok$.MODULE$.toString().equalsIgnoreCase(lastCheckResult)) {
      // we had OK recently (time window is a bit longer)
      return true;
    }

    return false;
  }

  public static void schedule() {
    log.info("scheduling electrum check work");
    final PeriodicWorkRequest.Builder work = new PeriodicWorkRequest.Builder(CheckElectrumWorker.class, 22, TimeUnit.HOURS, 12, TimeUnit.HOURS)
      .addTag(ELECTRUM_CHECK_WORKER_TAG);
    WorkManager.getInstance().enqueueUniquePeriodicWork(ELECTRUM_CHECK_WORKER_TAG, ExistingPeriodicWorkPolicy.REPLACE, work.build());
  }

  private void showNotification(@NonNull final Context context, final boolean isAlert) {
    final Intent startIntent = new Intent(context, StartupActivity.class);
    startIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    final String title = context.getString(isAlert ? R.string.notif_electrum_check_alert_title : R.string.notif_electrum_check_reminder_title);
    final String message = context.getString(isAlert ? R.string.notif_electrum_check_alert_message : R.string.notif_electrum_check_reminder_message);
    final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Constants.NOTIF_CHANNEL_START_REMINDER_ID)
      .setSmallIcon(R.drawable.eclair_256x256)
      .setContentTitle(title)
      .setContentText(message)
      .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
      .setContentIntent(PendingIntent.getActivity(context, Constants.NOTIF_START_REMINDER_REQUEST_CODE, startIntent, PendingIntent.FLAG_UPDATE_CURRENT))
      .setOngoing(isAlert)
      .setAutoCancel(true);
    NotificationManagerCompat.from(context).notify(Constants.NOTIF_START_REMINDER_REQUEST_CODE, builder.build());
  }
}
