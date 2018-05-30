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

  /* ----------- FILES NAMES ------------ */
  String ECLAIR_DATADIR = "eclair-wallet-data";

  /* ----------- SETTINGS ------------ */

  String SETTING_SHOW_DISCLAIMER = "showDisclaimer";
  String SETTING_SHOW_INTRO = "showIntro";
  String SETTING_LAST_USED_VERSION = "last_used_version";

  // currencies
  String SETTING_SELECTED_FIAT_CURRENCY = "fiat_currency";
  String FIAT_EURO = "eur";
  String FIAT_USD = "usd";
  String SETTING_BTC_UNIT = "btc_unit";
  String SETTING_BTC_PATTERN = "btc_pattern";
  String SETTING_DISPLAY_IN_FIAT = "display_in_fiat";
  String SETTING_LAST_KNOWN_RATE_BTC_ = "last_known_rate_btc_";

  // onchain explorer
  String SETTING_ONCHAIN_EXPLORER = "onchain_explorer";

  // lightning
  String SETTING_CAP_LIGHTNING_FEES = "cap_lightning_fees";

  /* ----------- SETTINGS - PIN CODE ------------ */

  String SETTINGS_SECURITY_FILE = BuildConfig.APPLICATION_ID + ".security_settings";
  String SETTING_ASK_PIN_FOR_SENSITIVE_ACTIONS = "ask_pin_for_sensitive_action";
  int PIN_LENGTH = 6;

  /* ----------- COINS ------------ */

  String SATOSHI_CODE = "sat";
  String BTC_CODE = "btc";

  /* ----------- FEE RATING ------------ */
  FeeRating FEE_RATING_SLOW = new FeeRating(0, "Slow (12h)");
  FeeRating FEE_RATING_MEDIUM = new FeeRating(1, "Medium (2h)");
  FeeRating FEE_RATING_FAST = new FeeRating(2, "Fast (20min)");
  FeeRating FEE_RATING_CUSTOM = new FeeRating(3, "Custom");

}
