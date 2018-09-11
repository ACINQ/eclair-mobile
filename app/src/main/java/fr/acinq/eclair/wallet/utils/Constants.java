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

import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.models.FeeRating;

public interface Constants {

  /**
   * time diff in sec beyond which a timestamp in a block header can safely be considered as late
   */
  long DESYNC_DIFF_TIMESTAMP_SEC = 6 * 60 * 60L;

  /* ----------- PERMISSIONS ------------ */

  int CAMERA_PERMISSION_REQUEST = 0;

  /* ----------- FILES NAMES ------------ */

  String ECLAIR_DATADIR = "eclair-wallet-data";
  String ECLAIR_DB_FILE = "eclair.sqlite";
  String NETWORK_DB_FILE = "network.sqlite";

  /* ----------- SETTINGS ------------ */

  String SETTING_SHOW_INTRO = "showIntro";
  String SETTING_LAST_USED_VERSION = "last_used_version";
  String SETTING_HAS_STARTED_ONCE = "has_started_once";
  String SETTING_CHANNELS_RESTORE_DONE = "channels_restore_done";
  String SETTING_CHANNELS_BACKUP_SEEN_ONCE = "channels_backup_seen_once";

  // currencies
  String SETTING_SELECTED_FIAT_CURRENCY = "fiat_currency";
  String FIAT_USD = "usd";
  String SETTING_BTC_UNIT = "btc_unit";
  String SETTING_BTC_PATTERN = "btc_pattern";
  String SETTING_DISPLAY_IN_FIAT = "display_in_fiat";
  String SETTING_LAST_KNOWN_RATE_BTC_ = "last_known_rate_btc_";

  // general
  String SETTING_HAPTIC_FEEDBACK = "haptic_feedback";

  // onchain explorer
  String SETTING_ONCHAIN_EXPLORER = "onchain_explorer";

  // lightning
  String SETTING_CAP_LIGHTNING_FEES = "cap_lightning_fees";

  // logging
  String SETTING_LOGS_OUTPUT = "node_logs_output";
  String LOGS_OUTPUT_NONE = "NONE";
  String LOGS_OUTPUT_LOCAL = "LOCAL";
  String LOGS_OUTPUT_PAPERTRAIL = "PAPER_TRAIL_APP";
  String SETTING_PAPERTRAIL_VISIBLE = "paper_trail_visible";
  String SETTING_PAPERTRAIL_HOST = "paper_trail_host";
  String SETTING_PAPERTRAIL_PORT = "paper_trail_port";

  /* ----------- SETTINGS - PIN CODE ------------ */

  String SETTINGS_SECURITY_FILE = BuildConfig.APPLICATION_ID + ".security_settings";
  String SETTING_ASK_PIN_FOR_SENSITIVE_ACTIONS = "ask_pin_for_sensitive_action";
  int PIN_LENGTH = 6;

  /* ----------- SETTINGS - CHANNELS BACKUP ------------ */

  String SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_ENABLED = "channels_backup_gdrive_enabled";
  String SETTING_CHANNELS_BACKUP_GOOGLEDRIVE_HAS_FAILED = "channels_backup_gdrive_has_failed";
  String BACKUP_META_DEVICE_ID = "backup_device_id";

  /* ----------- SETTINGS - WALLET ORIGIN ------------ */

  String SETTING_WALLET_ORIGIN = "wallet_origin";
  int WALLET_ORIGIN_FROM_SCRATCH = 1;
  int WALLET_ORIGIN_RESTORED_FROM_SEED = 2;


  /* ----------- COINS ------------ */

  String SATOSHI_CODE = "sat";
  String BTC_CODE = "btc";

  /* ----------- FEE RATING ------------ */

  FeeRating FEE_RATING_SLOW = new FeeRating(0, "Slow (12h)");
  FeeRating FEE_RATING_MEDIUM = new FeeRating(1, "Medium (2h)");
  FeeRating FEE_RATING_FAST = new FeeRating(2, "Fast (20min)");
  FeeRating FEE_RATING_CUSTOM = new FeeRating(3, "Custom");

  /* ----------- RESTORE BACKUP LAYOUT STEPS ------------ */

  int RESTORE_BACKUP_INIT = 1;
  int RESTORE_BACKUP_ERROR_PERMISSIONS = 2;
  int RESTORE_BACKUP_SEARCHING = 3;
  int RESTORE_BACKUP_SUCCESS = 4;
  int RESTORE_BACKUP_FAILURE = 5;
  int RESTORE_BACKUP_NO_BACKUP_FOUND = 6;
  int RESTORE_BACKUP_SYNC_RATELIMIT = 7;
  int RESTORE_BACKUP_DEVICE_ORIGIN_CONFLICT = 8;

  int IMPORT_WALLET_INIT = 1;
  int IMPORT_WALLET_SUCCESS = 2;

}
