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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import ch.qos.logback.classic.*;
import ch.qos.logback.classic.android.LogcatAppender;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import com.papertrailapp.logback.Syslog4jAppender;
import com.tozny.crypto.android.AesCbcWithIntegrity;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import fr.acinq.bitcoin.*;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.services.ChannelsBackupWorker;
import okhttp3.ResponseBody;
import org.json.JSONException;
import org.json.JSONObject;
import org.productivity.java.syslog4j.impl.net.tcp.ssl.SSLTCPNetSyslogConfig;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;
import scala.collection.JavaConverters;
import scodec.bits.ByteVector;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class WalletUtils {

  private final static org.slf4j.Logger log = LoggerFactory.getLogger(WalletUtils.class);

  public final static String UNENCRYPTED_SEED_NAME = "seed.dat";
  public final static String SEED_NAME = "enc_seed.dat";
  private final static String SEED_NAME_TEMP = "enc_seed_temp.dat";
  private final static String DECIMAL_SEPARATOR = String.valueOf(new DecimalFormat().getDecimalFormatSymbols().getDecimalSeparator());
  private static NumberFormat fiatFormat;

  private static void saveCurrency(final SharedPreferences.Editor editor, final JSONObject o, final String fiatCode) {
    float rate = -1.0f;
    try {
      rate = (float) o.getJSONObject(fiatCode).getDouble("last");
    } catch (Exception e) {
      log.debug("could not read {} from price api response", fiatCode);
    }
    App.RATES.put(fiatCode, rate);
    editor.putFloat(Constants.SETTING_LAST_KNOWN_RATE_BTC_ + fiatCode, rate);
  }

  private static void retrieveRateFromPrefs(final SharedPreferences prefs, final String fiatCode) {
    App.RATES.put(fiatCode, prefs.getFloat(Constants.SETTING_LAST_KNOWN_RATE_BTC_ + fiatCode, -1.0f));
  }

  public static void retrieveRatesFromPrefs(final SharedPreferences prefs) {
    retrieveRateFromPrefs(prefs, "AUD");
    retrieveRateFromPrefs(prefs, "BRL");
    retrieveRateFromPrefs(prefs, "CAD");
    retrieveRateFromPrefs(prefs, "CHF");
    retrieveRateFromPrefs(prefs, "CLP");
    retrieveRateFromPrefs(prefs, "CNY");
    retrieveRateFromPrefs(prefs, "DKK");
    retrieveRateFromPrefs(prefs, "EUR");
    retrieveRateFromPrefs(prefs, "GBP");
    retrieveRateFromPrefs(prefs, "HKD");
    retrieveRateFromPrefs(prefs, "INR");
    retrieveRateFromPrefs(prefs, "ISK");
    retrieveRateFromPrefs(prefs, "JPY");
    retrieveRateFromPrefs(prefs, "KRW");
    retrieveRateFromPrefs(prefs, "NZD");
    retrieveRateFromPrefs(prefs, "PLN");
    retrieveRateFromPrefs(prefs, "RUB");
    retrieveRateFromPrefs(prefs, "SEK");
    retrieveRateFromPrefs(prefs, "SGD");
    retrieveRateFromPrefs(prefs, "THB");
    retrieveRateFromPrefs(prefs, "TWD");
    retrieveRateFromPrefs(prefs, "USD");
  }

  public static void handleExchangeRateResponse(final SharedPreferences prefs, @NonNull final ResponseBody body) throws IOException, JSONException {
    final SharedPreferences.Editor editor = prefs.edit();
    JSONObject json = new JSONObject(body.string());
    saveCurrency(editor, json, "AUD"); // australian dollar
    saveCurrency(editor, json, "BRL"); // br real
    saveCurrency(editor, json, "CAD"); // canadian dollar
    saveCurrency(editor, json, "CHF"); // swiss franc
    saveCurrency(editor, json, "CLP"); // chilean pesos
    saveCurrency(editor, json, "CNY"); // yuan
    saveCurrency(editor, json, "DKK"); // denmark krone
    saveCurrency(editor, json, "EUR"); // euro
    saveCurrency(editor, json, "GBP"); // pound
    saveCurrency(editor, json, "HKD"); // hong kong dollar
    saveCurrency(editor, json, "INR"); // indian rupee
    saveCurrency(editor, json, "ISK"); // icelandic kròna
    saveCurrency(editor, json, "JPY"); // yen
    saveCurrency(editor, json, "KRW"); // won
    saveCurrency(editor, json, "NZD"); // nz dollar
    saveCurrency(editor, json, "PLN"); // zloty
    saveCurrency(editor, json, "RUB"); // ruble
    saveCurrency(editor, json, "SEK"); // swedish krona
    saveCurrency(editor, json, "SGD"); // singapore dollar
    saveCurrency(editor, json, "THB"); // thai baht
    saveCurrency(editor, json, "TWD"); // taiwan dollar
    saveCurrency(editor, json, "USD"); // usd
    editor.apply();
  }

  public static View.OnClickListener getOpenTxListener(final String txId) {
    return v -> {
      String uri = PreferenceManager.getDefaultSharedPreferences(v.getContext())
        .getString(Constants.SETTING_ONCHAIN_EXPLORER, Constants.DEFAULT_ONCHAIN_EXPLORER);
      try {
        if (uri != null && !uri.endsWith("/")) {
          uri += "/";
        }
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri + txId));
        v.getContext().startActivity(browserIntent);
      } catch (Throwable t) {
        log.warn("could not open explorer with uri={}{}", uri, txId);
        Toast.makeText(v.getContext(), "Could not open explorer", Toast.LENGTH_SHORT).show();
      }
    };
  }

  public static byte[] mnemonicsToSeed(List<String> mnemonics, String passphrase) {
    final byte[] bytes = MnemonicCode.toSeed(JavaConverters.collectionAsScalaIterableConverter(mnemonics).asScala().toSeq(), passphrase).toArray();
    final byte[] seed = Hex.encode(bytes);
    return seed;
  }

  public static byte[] mnemonicsToSeed(String mnemonics, String passphrase) {
    return mnemonicsToSeed(Lists.newArrayList(mnemonics.split(" ")), passphrase);
  }

  private static byte[] readSeedFile(final File datadir, final String seedFileName, final String password) throws IOException, IllegalAccessException, GeneralSecurityException {
    if (!datadir.exists()) {
      throw new RuntimeException("datadir does not exist");
    }
    final File seedFile = new File(datadir, seedFileName);
    if (!seedFile.exists() || !seedFile.canRead() || !seedFile.isFile()) {
      throw new RuntimeException("seed file does not exist or can not be read");
    }
    final byte[] fileContent = Files.toByteArray(seedFile);
    final EncryptedSeed encryptedSeed = EncryptedSeed.read(fileContent);
    return encryptedSeed.decrypt(password);
  }

  public static byte[] readSeedFile(final File datadir, final String password) throws IOException, IllegalAccessException, GeneralSecurityException {
    return readSeedFile(datadir, SEED_NAME, password);
  }

  public static void writeSeedFile(final File datadir, final byte[] seed, final String password) throws IOException {
    try {
      if (!datadir.exists()) {
        datadir.mkdir();
      }
      // encrypt and write in temp file
      final File temp = new File(datadir, SEED_NAME_TEMP);
      final EncryptedSeed encryptedSeed = EncryptedSeed.encrypt(seed, password, EncryptedSeed.SEED_FILE_VERSION_1);
      Files.write(encryptedSeed.write(), temp);
      // decrypt temp file and check validity; if correct, move temp file to final file
      final byte[] checkSeed = readSeedFile(datadir, SEED_NAME_TEMP, password);
      if (!AesCbcWithIntegrity.constantTimeEq(checkSeed, seed)) {
        throw new GeneralSecurityException();
      } else {
        Files.move(temp, new File(datadir, SEED_NAME));
      }
    } catch (SecurityException e) {
      throw new RuntimeException("could not create datadir");
    } catch (IOException e) {
      throw new RuntimeException("could not write seed file");
    } catch (Exception e) {
      throw new RuntimeException("could not create seed");
    }
  }

  private static NumberFormat getFiatFormat() {
    if (fiatFormat == null) {
      fiatFormat = NumberFormat.getInstance();
      fiatFormat.setMinimumFractionDigits(2);
      fiatFormat.setMaximumFractionDigits(2);
    }
    return fiatFormat;
  }

  public static boolean shouldDisplayInFiat(final SharedPreferences prefs) {
    return prefs.getBoolean(Constants.SETTING_DISPLAY_IN_FIAT, false);
  }

  /**
   * Gets the user's preferred fiat currency. Default is USD.
   */
  public static String getPreferredFiat(final SharedPreferences prefs) {
    return prefs.getString(Constants.SETTING_SELECTED_FIAT_CURRENCY, Constants.FIAT_USD).toUpperCase();
  }

  private final static String NO_FIAT_RATE = "--";

  /**
   * Converts bitcoin amount to the fiat currency preferred by the user.
   *
   * @param amountMsat amount in milli satoshis
   * @param fiatCode   fiat currency code (USD, EUR, RUB, JPY, ...)
   * @return localized formatted string of the converted amount
   */
  public static double convertMsatToFiat(final long amountMsat, final String fiatCode) {
    final double rate = App.RATES.containsKey(fiatCode) ? App.RATES.get(fiatCode) : -1.0f;
    return package$.MODULE$.millisatoshi2btc(new MilliSatoshi(amountMsat)).amount().doubleValue() * rate;
  }

  /**
   * Converts bitcoin amount to the fiat currency preferred by the user.
   *
   * @param amountMsat amount in milli satoshis
   * @param fiatCode   fiat currency code (USD, EUR, RUB, JPY, ...)
   * @return localized formatted string of the converted amount
   */
  public static String formatMsatToFiat(final long amountMsat, final String fiatCode) {
    final double fiatValue = convertMsatToFiat(amountMsat, fiatCode);
    if (fiatValue < 0) return NO_FIAT_RATE;
    return getFiatFormat().format(fiatValue);
  }

  public static String formatMsatToFiatWithUnit(final long amountMsat, final String fiatCode) {
    return formatMsatToFiat(amountMsat, fiatCode) + " " + fiatCode.toUpperCase();
  }

  public static String formatSatToFiat(final Satoshi amount, final String fiatCode) {
    final double rate = App.RATES.containsKey(fiatCode) ? App.RATES.get(fiatCode) : -1.0f;
    if (rate < 0) return NO_FIAT_RATE;
    return getFiatFormat().format(package$.MODULE$.satoshi2btc(amount).amount().doubleValue() * rate);
  }

  public static String formatSatToFiatWithUnit(final Satoshi amount, final String fiatCode) {
    return formatSatToFiat(amount, fiatCode) + " " + fiatCode.toUpperCase();
  }

  public static CoinUnit getPreferredCoinUnit(final SharedPreferences prefs) {
    return fr.acinq.eclair.CoinUtils.getUnitFromString(prefs.getString(Constants.SETTING_BTC_UNIT, Constants.BTC_CODE));
  }

  /**
   * Prints a stringified amount in a text view. Decimal part if present is smaller than int part.
   */
  @SuppressLint("SetTextI18n")
  public static void printAmountInView(final TextView view, final String amount, final String direction) {
    final String[] amountParts = amount.split(Pattern.quote(DECIMAL_SEPARATOR));
    if (amountParts.length == 2) {
      view.setText(Html.fromHtml(view.getContext().getString(R.string.pretty_amount_value, direction + amountParts[0] + DECIMAL_SEPARATOR, amountParts[1])));
    } else {
      view.setText(direction + amount);
    }
  }

  public static void printAmountInView(final TextView view, final String amount) {
    printAmountInView(view, amount, "");
  }

  /**
   * Return amount as Long, in millisatoshi
   */
  public static long getLongAmountFromInvoice(PaymentRequest paymentRequest) {
    return paymentRequest.amount().isEmpty() ? 0 : paymentRequest.amount().get().amount();
  }

  public static MilliSatoshi getAmountFromInvoice(PaymentRequest paymentRequest) {
    return paymentRequest.amount().isEmpty() ? new MilliSatoshi(0) : paymentRequest.amount().get();
  }

  public static ByteVector32 getChainHash() {
    return "mainnet".equals(BuildConfig.CHAIN) ? Block.LivenetGenesisBlock().hash() : Block.TestnetGenesisBlock().hash();
  }

  public static File getDatadir(final Context context) {
    return new File(context.getFilesDir(), Constants.ECLAIR_DATADIR);
  }

  public static File getChainDatadir(final Context context) {
    return new File(getDatadir(context), BuildConfig.CHAIN);
  }

  public static File getWalletDBFile(final Context context) {
    return new File(getChainDatadir(context), Constants.WALLET_DB_FILE);
  }

  public static File getNetworkDBFile(final Context context) {
    return new File(getChainDatadir(context), Constants.NETWORK_DB_FILE);
  }

  public static File getEclairDBFile(final Context context) {
    return new File(getChainDatadir(context), Constants.ECLAIR_DB_FILE);
  }

  /**
   * Retrieve the actual eclair backup file created by eclair core. This is the file that should be backed up.
   */
  public static File getEclairDBFileBak(final Context context) {
    return new File(getChainDatadir(context), Constants.ECLAIR_DB_FILE_BAK);
  }

  public static String getEclairBackupFileName(final String seedHash) {
    return "eclair_" + BuildConfig.CHAIN + "_" + seedHash + ".bkup";
  }

  public static OneTimeWorkRequest generateBackupRequest(final String seedHash, final ByteVector32 backupKey) {
    return new OneTimeWorkRequest.Builder(ChannelsBackupWorker.class)
      .setInputData(new Data.Builder()
        .putString(ChannelsBackupWorker.BACKUP_NAME_INPUT, WalletUtils.getEclairBackupFileName(seedHash))
        .putString(ChannelsBackupWorker.BACKUP_KEY_INPUT, backupKey.toString())
        .build())
      .setInitialDelay(2, TimeUnit.SECONDS)
      .addTag("ChannelsBackupWork")
      .build();
  }

  public static String toAscii(final ByteVector b) {
    final byte[] bytes = b.toArray();
    return new String(bytes, StandardCharsets.US_ASCII);
  }

  public static String getDeviceId(final Context context) {
    return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
  }

  public static void setupLogging(final Context context) {
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    switch (prefs.getString(Constants.SETTING_LOGS_OUTPUT, Constants.LOGS_OUTPUT_NONE)) {
      case Constants.LOGS_OUTPUT_LOCAL:
        try {
          setupLocalLogging(context);
        } catch (EclairException.ExternalStorageUnavailableException e) {
          Log.e("WalletUtils", "external storage is not available, cannot enable local logging");
        }
        break;
      case Constants.LOGS_OUTPUT_PAPERTRAIL:
        setupPapertrailLogging(prefs.getString(Constants.SETTING_PAPERTRAIL_HOST, ""),
          prefs.getInt(Constants.SETTING_PAPERTRAIL_PORT, 12345));
        break;
      default:
        if (BuildConfig.DEBUG) {
          setupLogcatLogging();
        } else {
          disableLogging();
        }
        break;
    }
  }

  public static void disableLogging() {
    final LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
    lc.reset();
    lc.stop();
  }

  private static void setupLogcatLogging() {
    final LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
    lc.reset();

    final PatternLayoutEncoder tagEncoder = new PatternLayoutEncoder();
    tagEncoder.setContext(lc);
    tagEncoder.setPattern("%logger{12}");
    tagEncoder.start();

    final PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setContext(lc);
    encoder.setPattern("%X{nodeId}%X{channelId} - %msg%ex{24}%n");
    encoder.start();

    final LogcatAppender logcatAppender = new LogcatAppender();
    logcatAppender.setContext(lc);
    logcatAppender.setEncoder(encoder);
    logcatAppender.setTagEncoder(tagEncoder);
    logcatAppender.start();

    useAppender(lc, logcatAppender);
  }

  /**
   * Sets up an index-based rolling policy with a max file size of 4MB.
   */
  public static void setupLocalLogging(final Context context) throws EclairException.ExternalStorageUnavailableException {
    if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
      throw new EclairException.ExternalStorageUnavailableException();
    }

    final LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
    lc.reset();

    final File logsDir = context.getExternalFilesDir(Constants.LOGS_DIR);
    if (!logsDir.exists()) logsDir.mkdirs();

    final PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setContext(lc);
    encoder.setPattern(Constants.ENCODER_PATTERN);
    encoder.start();

    final RollingFileAppender<ILoggingEvent> rollingFileAppender = new RollingFileAppender<>();
    rollingFileAppender.setContext(lc);
    rollingFileAppender.setFile(new File(logsDir, Constants.CURRENT_LOG_FILE).getAbsolutePath());

    final FixedWindowRollingPolicy rollingPolicy = new FixedWindowRollingPolicy();
    rollingPolicy.setContext(lc);
    rollingPolicy.setParent(rollingFileAppender);
    rollingPolicy.setMinIndex(1);
    rollingPolicy.setMaxIndex(2);
    rollingPolicy.setFileNamePattern(new File(logsDir, Constants.ARCHIVED_LOG_FILE).getAbsolutePath());
    rollingPolicy.start();

    final SizeBasedTriggeringPolicy<ILoggingEvent> triggeringPolicy = new SizeBasedTriggeringPolicy<>();
    triggeringPolicy.setContext(lc);
    triggeringPolicy.setMaxFileSize("4mb");
    triggeringPolicy.start();

    rollingFileAppender.setEncoder(encoder);
    rollingFileAppender.setRollingPolicy(rollingPolicy);
    rollingFileAppender.setTriggeringPolicy(triggeringPolicy);
    rollingFileAppender.start();

    useAppender(lc, rollingFileAppender);
  }

  /**
   * Sets up an index-based rolling policy with a max file size of 4MB.
   */
  public static void setupPapertrailLogging(final String host, final int port) {
    final LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
    lc.reset();

    final PatternLayout patternLayout = new PatternLayout();
    patternLayout.setContext(lc);
    patternLayout.setPattern(Constants.ENCODER_PATTERN);
    patternLayout.start();

    final SSLTCPNetSyslogConfig syslogConfig = new SSLTCPNetSyslogConfig();
    syslogConfig.setHost(host);
    syslogConfig.setPort(port);
    syslogConfig.setIdent("eclair-wallet");
    syslogConfig.setSendLocalName(false);
    syslogConfig.setSendLocalTimestamp(false);
    syslogConfig.setMaxMessageLength(128000);

    final Syslog4jAppender syslogAppender = new Syslog4jAppender();
    syslogAppender.setContext(lc);
    syslogAppender.setName("syslog");
    syslogAppender.setLayout(patternLayout);
    syslogAppender.setSyslogConfig(syslogConfig);
    syslogAppender.start();

    final AsyncAppender asyncAppender = new AsyncAppender();
    asyncAppender.setContext(lc);
    asyncAppender.addAppender(syslogAppender);
    asyncAppender.start();

    useAppender(lc, asyncAppender);
  }

  private static void useAppender(final LoggerContext lc, final Appender<ILoggingEvent> appender) {
    lc.getLogger("fr.acinq.eclair.crypto").setLevel(Level.WARN); // ChaCha20Poly1305 spams a lot in debug
    if (BuildConfig.DEBUG) {
      lc.getLogger("io.netty").setLevel(Level.DEBUG);
    } else {
      lc.getLogger("io.netty").setLevel(Level.WARN);
    }
    final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    root.setLevel(BuildConfig.DEBUG ? Level.DEBUG : Level.INFO);
    root.addAppender(appender);
  }

  /**
   * Builds a TypeSafe configuration to override the default conf of the node setup. Returns an empty config if no configuration entry must be overridden.
   * <p>
   * If the user has set a preferred electrum server, retrieves it from the prefs and adds it to the configuration.
   */
  public static Config getOverrideConfig(final SharedPreferences prefs) {
    final String prefsElectrumAddress = prefs.getString(Constants.CUSTOM_ELECTRUM_SERVER, "").trim();
    if (!Strings.isNullOrEmpty(prefsElectrumAddress)) {
      try {
        final HostAndPort address = HostAndPort.fromString(prefsElectrumAddress).withDefaultPort(50002);
        final Map<String, Object> conf = new HashMap<>();
        if (!Strings.isNullOrEmpty(address.getHost())) {
          conf.put("eclair.electrum.host", address.getHost());
          conf.put("eclair.electrum.port", address.getPort());
          // custom server certificate must be valid
          conf.put("eclair.electrum.ssl", "strict");
          return ConfigFactory.parseMap(conf);
        }
      } catch (Exception e) {
        log.error("could not read custom electrum address=" + prefsElectrumAddress, e);
      }
    }
    return ConfigFactory.empty();
  }
}
