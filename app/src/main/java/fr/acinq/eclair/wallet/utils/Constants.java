package fr.acinq.eclair.wallet.utils;

import java.util.HashMap;
import java.util.Map;

import fr.acinq.eclair.wallet.BuildConfig;

public interface Constants {

  /* ----------- SETTINGS ------------ */

  String SETTING_SHOW_DISCLAIMER = "showDisclaimer";
  String SETTING_SHOW_RECOVERY = "showRecovery";
  String SETTING_SHOW_INTRO = "showIntro";

  String SETTING_SELECTED_FIAT_CURRENCY = "fiat_currency";
  String FIAT_EURO = "eur";
  String FIAT_USD = "usd";

  String SETTING_BTC_UNIT = "btc_unit";
  String SETTING_LIGHTNING_MAX_FEE = "lightning_max_fee";
  String SETTING_LIGHTNING_MAX_FEE_VALUE = "lightning_max_fee_value";

  /* ----------- SETTINGS - PIN CODES ------------ */

  String SETTINGS_SECURITY_FILE = BuildConfig.APPLICATION_ID + ".security_settings";
  String SETTING_PIN_VALUE = "PIN_VALUE";
  String SETTING_PIN_LAST_UPDATE = "PIN_LAST_UPDATE";
  String PIN_UNDEFINED_VALUE = "";
  int PIN_LENGTH = 6;

  /* ----------- COINS ------------ */

  String MILLI_SATOSHI_CODE = "msat";
  String SATOSHI_CODE = "sat";
  String MILLI_BTC_CODE = "mbtc";
  String BTC_CODE = "btc";


}
