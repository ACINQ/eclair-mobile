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

package fr.acinq.eclair.wallet.utils;

import fr.acinq.eclair.io.NodeURI;
import fr.acinq.eclair.wallet.BuildConfig;

public interface Constants {

  /**
   * time diff in sec beyond which a timestamp in a block header can safely be considered as late
   * we use a fairly large value here, because we want to notify users that they can use the wallet as soon
   * as we're connected to a "good" electrum server and sync has begun.
   * 99% users will be able to use the wallet straight away
   * in the rare instances where they were able to create and try to publish a tx during the syncing phase
   * and one of their utxos got spent then publishing the tx will fail and the wallet will be updated.
   * the time window for this is just a few seconds
   */
  long DESYNC_DIFF_TIMESTAMP_SEC = 60L * 60 * 24 * 30; // 30 days
  long ONE_MIN_MS = 1000L * 60;
  long ONE_HOUR_MS = ONE_MIN_MS * 60;
  long ONE_DAY_MS = ONE_HOUR_MS * 24;

  /* ----------- CHAIN CONSTANTS ------------- */

  /**
   * Minimal blockchain height that the wallet should reach before being considered ready (value depends on the chain used).
   */
  long MIN_BLOCK_HEIGHT = BuildConfig.CHAIN == "mainnet" ? 500 * 1000L
    : BuildConfig.CHAIN == "testnet" ? 1000 * 1000L
    : 0;

  /* ----------- PERMISSIONS & REQUEST CODES ------------ */

  int CAMERA_PERMISSION_REQUEST = 0;
  int OPEN_CONNECTION_REQUEST_CODE = 42;

  /* ----------- STARTUP OPTION ----------- */

  String CUSTOM_ELECTRUM_SERVER = "custom_electrum_server";

  /* ----------- DIR & FILES NAMES ------------ */

  String ECLAIR_DATADIR = "eclair-wallet-data";
  String ECLAIR_DB_FILE = "eclair.sqlite";
  String NETWORK_DB_FILE = "network.sqlite";
  String WALLET_DB_FILE = "wallet.sqlite";
  String LOGS_DIR = "logs";
  String CURRENT_LOG_FILE = "eclair-wallet.log";
  String ARCHIVED_LOG_FILE = "eclair-wallet.archive-%i.log";

  /* ----------- SETTINGS ------------ */

  String SETTING_SHOW_INTRO = "showIntro";
  String SETTING_LAST_USED_VERSION = "last_used_version";
  String SETTING_HAS_STARTED_ONCE = "has_started_once";
  String SETTING_CHANNELS_RESTORE_DONE = "channels_restore_done";
  String SETTING_CHANNELS_BACKUP_SEEN_ONCE = "channels_backup_seen_once";
  String SETTING_LAST_SUCCESSFUL_BOOT_DATE = "last_successful_boot_date";
  String SETTING_ELECTRUM_CHECK_LAST_ATTEMPT_TIMESTAMP = "electrum_check_last_attempt_timestamp";
  String SETTING_ELECTRUM_CHECK_LAST_OUTCOME_TIMESTAMP = "electrum_check_last_date";
  String SETTING_ELECTRUM_CHECK_LAST_OUTCOME_RESULT = "electrum_check_last_result";
  String SETTING_BACKGROUND_CANNOT_RUN_WARNING = "app_background_cannot_run_warning";
  String SETTING_LAST_UPDATE_WARNING_TIMESTAMP = "last_update_warning_timestamp";

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
  String SETTING_ENABLE_LIGHTNING_INBOUND_PAYMENTS = "enable_lightning_inbound_payments";
  String SETTING_PAYMENT_REQUEST_DEFAULT_DESCRIPTION = "payment_request_default_description";
  String SETTING_PAYMENT_REQUEST_EXPIRY = "payment_request_expiry";

  // logging
  String ENCODER_PATTERN = "%d %-5level %logger{24} %X{nodeId}%X{channelId} - %msg%ex{24}%n";
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

  int FEE_RATING_SLOW = 0;
  int FEE_RATING_MEDIUM = 1;
  int FEE_RATING_FAST = 2;
  int FEE_RATING_CUSTOM = 3;

  /* ----------- RESTORE BACKUP LAYOUT STEPS ------------ */

  int RESTORE_BACKUP_INIT = 1;
  int RESTORE_BACKUP_ERROR_PERMISSIONS = 2;
  int RESTORE_BACKUP_SEARCHING = 3;
  int RESTORE_BACKUP_SUCCESS = 4;
  int RESTORE_BACKUP_FAILURE = 5;
  int RESTORE_BACKUP_NO_BACKUP_FOUND = 6;
  int RESTORE_BACKUP_SYNC_RATELIMIT = 7;
  int RESTORE_BACKUP_DEVICE_ORIGIN_CONFLICT = 8;

  /* ----------- WALLET SEED SPAWN PROCESS ------------ */

  int SEED_SPAWN_ENCRYPTION = 8;
  int SEED_SPAWN_COMPLETE = 9;
  int SEED_SPAWN_ERROR = 10;

  /* ----------- NODE CONNECTION STEPS ------------ */

  int NODE_CONNECT_READY = 0;
  int NODE_CONNECT_CONNECTING = 1;
  int NODE_CONNECT_SUCCESS = 2;

  /* --------- REFRESH SCHEDULER MESSAGES ----------- */

  String WAKE_UP = "wake_up";
  String REFRESH = "refresh";

  /* ----------- NOTIFICATION CHANNEL IDS ------------ */

  String NOTIF_CHANNEL_CLOSED_ID = "CHANNEL_CLOSED";
  String NOTIF_CHANNEL_START_REMINDER_ID = "START_APP";
  String NOTIF_CHANNEL_RECEIVED_LN_PAYMENT_ID = "RECEIVED_LN_PAYMENT";
  int NOTIF_START_REMINDER_REQUEST_CODE = 437165794;

  /* ------------ API URLS ------------ */

  String PRICE_RATE_API = "https://blockchain.info/fr/ticker";
  NodeURI ACINQ_NODE_URI = NodeURI.parse("03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134@endurance.acinq.co:9735");
  String WALLET_CONTEXT_SOURCE = "https://acinq.co/mobile/walletcontext.json";
  String DEFAULT_ONCHAIN_EXPLORER = "https://api.blockcypher.com/v1/btc/test3/txs/";

}
