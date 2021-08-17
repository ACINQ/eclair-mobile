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
package fr.acinq.eclair.wallet.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.preference.PreferenceManager
import android.provider.Settings
import android.text.Html
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.android.LogcatAppender
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy
import ch.qos.logback.core.util.FileSize
import com.google.common.base.Strings
import com.google.common.io.Files
import com.google.common.net.HostAndPort
import com.tozny.crypto.android.AesCbcWithIntegrity
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import fr.acinq.bitcoin.*
import fr.acinq.eclair.CoinUnit
import fr.acinq.eclair.CoinUtils
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.wallet.App
import fr.acinq.eclair.wallet.BuildConfig
import fr.acinq.eclair.wallet.R
import fr.acinq.eclair.wallet.utils.EclairException.ExternalStorageUnavailableException
import okhttp3.ResponseBody
import org.bouncycastle.util.encoders.Hex
import org.json.JSONException
import org.json.JSONObject
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters
import scala.math.BigDecimal
import scala.math.`BigDecimal$`
import scodec.bits.ByteVector
import scodec.bits.`ByteVector$`
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.*
import java.util.regex.Pattern

object WalletUtils {
  private val log = LoggerFactory.getLogger(WalletUtils::class.java)
  const val UNENCRYPTED_SEED_NAME = "seed.dat"
  const val SEED_NAME = "enc_seed.dat"
  private const val SEED_NAME_TEMP = "enc_seed_temp.dat"
  private val DECIMAL_SEPARATOR = DecimalFormat().decimalFormatSymbols.decimalSeparator.toString()
  private var fiatFormat: NumberFormat = NumberFormat.getInstance().apply {
    setMinimumFractionDigits(2)
    setMaximumFractionDigits(2)
  }

  private fun saveCurrency(editor: SharedPreferences.Editor, o: JSONObject, fiatCode: String) {
    var rate = -1.0f
    try {
      rate = o.getJSONObject(fiatCode).getDouble("last").toFloat()
    } catch (e: Exception) {
      log.debug("could not read {} from price api response", fiatCode)
    }
    App.RATES[fiatCode] = rate
    editor.putFloat(Constants.SETTING_LAST_KNOWN_RATE_BTC_ + fiatCode, rate)
  }

  private fun retrieveRateFromPrefs(prefs: SharedPreferences, fiatCode: String) {
    App.RATES[fiatCode] = prefs.getFloat(
      Constants.SETTING_LAST_KNOWN_RATE_BTC_ + fiatCode,
      -1.0f
    )
  }

  @JvmStatic
  fun retrieveRatesFromPrefs(prefs: SharedPreferences) {
    retrieveRateFromPrefs(prefs, "AUD")
    retrieveRateFromPrefs(prefs, "BRL")
    retrieveRateFromPrefs(prefs, "CAD")
    retrieveRateFromPrefs(prefs, "CHF")
    retrieveRateFromPrefs(prefs, "CLP")
    retrieveRateFromPrefs(prefs, "CNY")
    retrieveRateFromPrefs(prefs, "DKK")
    retrieveRateFromPrefs(prefs, "EUR")
    retrieveRateFromPrefs(prefs, "GBP")
    retrieveRateFromPrefs(prefs, "HKD")
    retrieveRateFromPrefs(prefs, "INR")
    retrieveRateFromPrefs(prefs, "ISK")
    retrieveRateFromPrefs(prefs, "JPY")
    retrieveRateFromPrefs(prefs, "KRW")
    retrieveRateFromPrefs(prefs, "NZD")
    retrieveRateFromPrefs(prefs, "PLN")
    retrieveRateFromPrefs(prefs, "RUB")
    retrieveRateFromPrefs(prefs, "SEK")
    retrieveRateFromPrefs(prefs, "SGD")
    retrieveRateFromPrefs(prefs, "THB")
    retrieveRateFromPrefs(prefs, "TWD")
    retrieveRateFromPrefs(prefs, "USD")
  }

  @JvmStatic
  @Throws(IOException::class, JSONException::class)
  fun handleExchangeRateResponse(prefs: SharedPreferences, body: ResponseBody) {
    val editor = prefs.edit()
    val json = JSONObject(body.string())
    saveCurrency(editor, json, "AUD") // australian dollar
    saveCurrency(editor, json, "BRL") // br real
    saveCurrency(editor, json, "CAD") // canadian dollar
    saveCurrency(editor, json, "CHF") // swiss franc
    saveCurrency(editor, json, "CLP") // chilean pesos
    saveCurrency(editor, json, "CNY") // yuan
    saveCurrency(editor, json, "DKK") // denmark krone
    saveCurrency(editor, json, "EUR") // euro
    saveCurrency(editor, json, "GBP") // pound
    saveCurrency(editor, json, "HKD") // hong kong dollar
    saveCurrency(editor, json, "INR") // indian rupee
    saveCurrency(editor, json, "ISK") // icelandic kròna
    saveCurrency(editor, json, "JPY") // yen
    saveCurrency(editor, json, "KRW") // won
    saveCurrency(editor, json, "NZD") // nz dollar
    saveCurrency(editor, json, "PLN") // zloty
    saveCurrency(editor, json, "RUB") // ruble
    saveCurrency(editor, json, "SEK") // swedish krona
    saveCurrency(editor, json, "SGD") // singapore dollar
    saveCurrency(editor, json, "THB") // thai baht
    saveCurrency(editor, json, "TWD") // taiwan dollar
    saveCurrency(editor, json, "USD") // usd
    editor.apply()
  }

  @JvmStatic
  fun getOpenTxListener(txId: String): View.OnClickListener {
    return View.OnClickListener { v: View ->
      var uri = PreferenceManager.getDefaultSharedPreferences(v.context)
        .getString(Constants.SETTING_ONCHAIN_EXPLORER, Constants.DEFAULT_ONCHAIN_EXPLORER)
      try {
        if (uri != null && !uri.endsWith("/")) {
          uri += "/"
        }
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri + txId))
        v.context.startActivity(browserIntent)
      } catch (t: Throwable) {
        log.warn("could not open explorer with uri={}{}", uri, txId)
        Toast.makeText(v.context, "Could not open explorer", Toast.LENGTH_SHORT).show()
      }
    }
  }

  /** Return a hex-encoded Json byte array containing the mnemonics+passphrase */
  @JvmStatic
  fun encodeMnemonics(version: Int, words: List<String>, passphrase: String?): ByteArray {
    return when(version) {
      EncryptedSeed.SEED_FILE_VERSION_1 -> mnemonicsToSeed(words, passphrase)
      EncryptedSeed.SEED_FILE_VERSION_2 -> JSONObject()
        .put("words", words.joinToString(" "))
        .put("passphrase", passphrase ?: "")
        .toString(0).toByteArray(Charsets.UTF_8)
      else -> throw RuntimeException("unable to extract mnemonics, unhandled version=$version")
    }.let {
      Hex.encode(it)
    }
  }

  /** Extract the mnemonic phrase + passphrase from a decrypted byte array. */
  @JvmStatic
  fun decodeV2MnemonicsBlob(blob: ByteArray): Pair<List<String>, String> {
    return JSONObject(byteArray2String(blob)).let {
      it.getString("words").split(" ") to it.getString("passphrase")
    }
  }

  /** Get the seed from a the mnemonic phrase + passphrase from a decrypted seed byte array. */
  @JvmStatic
  fun decodeSeed(encryptedSeed: EncryptedSeed, blob: ByteArray): ByteVector {
    return when(encryptedSeed.version) {
      EncryptedSeed.SEED_FILE_VERSION_1 -> Hex.decode(blob)
      EncryptedSeed.SEED_FILE_VERSION_2 -> {
        val (words, passphrase) = decodeV2MnemonicsBlob(blob)
        mnemonicsToSeed(words, passphrase)
      }
      else -> throw RuntimeException("unable to extract mnemonics, unhandled version=${encryptedSeed.version}")
    }.let {
      `ByteVector$`.`MODULE$`.apply(it)
    }
  }

  @JvmStatic
  fun mnemonicsToSeed(words: List<String>?, passphrase: String?): ByteArray {
    return MnemonicCode.toSeed(JavaConverters.collectionAsScalaIterableConverter(words).asScala().toSeq(), passphrase).toArray()
  }

  fun byteArray2String(array: ByteArray): String = String(Hex.decode(array), Charsets.UTF_8)

  @Throws(IOException::class, IllegalAccessException::class)
  fun readSeedFile(datadir: File, seedFileName: String): EncryptedSeed {
    if (!datadir.exists()) {
      throw RuntimeException("datadir does not exist")
    }
    val seedFile = File(datadir, seedFileName)
    if (!seedFile.exists() || !seedFile.canRead() || !seedFile.isFile) {
      throw RuntimeException("seed file does not exist or can not be read")
    }
    val fileContent = Files.toByteArray(seedFile)
    val encryptedSeed = EncryptedSeed.read(fileContent)
    return encryptedSeed
  }

  @JvmStatic
  @Throws(IOException::class, IllegalAccessException::class, GeneralSecurityException::class)
  fun readSeedAndDecrypt(datadir: File, password: String): Pair<EncryptedSeed, ByteArray> {
    return readSeedFile(datadir, SEED_NAME).let { it to it.decrypt(password) }
  }

  /**
   * Encrypt and write the seed blob to disk. When using version=1, the data blob is expected to be the seed ; with version 2, it's expected to be the mnemonics.
   */
  @JvmStatic
  @Throws(IOException::class)
  fun writeSeedFile(datadir: File, data: ByteArray?, password: String, version: Int) {
    try {
      if (!datadir.exists()) {
        datadir.mkdir()
      }
      // encrypt and write in temp file
      val temp = File(datadir, SEED_NAME_TEMP)
      val encryptedSeed = EncryptedSeed.encrypt(data, password, version)
      Files.write(encryptedSeed.write(), temp)
      // decrypt temp file and check validity; if correct, move temp file to final file
      val checkBlob = readSeedFile(datadir, SEED_NAME_TEMP).decrypt(password)
      if (!AesCbcWithIntegrity.constantTimeEq(checkBlob, data)) {
        throw GeneralSecurityException()
      } else {
        Files.move(temp, File(datadir, SEED_NAME))
      }
    } catch (e: SecurityException) {
      throw RuntimeException("could not create datadir", e)
    } catch (e: IOException) {
      throw RuntimeException("could not write seed file", e)
    } catch (e: Exception) {
      throw RuntimeException("could not create seed: ", e)
    }
  }

  @JvmStatic
  fun shouldDisplayInFiat(prefs: SharedPreferences): Boolean {
    return prefs.getBoolean(Constants.SETTING_DISPLAY_IN_FIAT, false)
  }

  /**
   * Gets the user's preferred fiat currency. Default is USD.
   */
  @JvmStatic
  fun getPreferredFiat(prefs: SharedPreferences): String {
    return prefs.getString(
      Constants.SETTING_SELECTED_FIAT_CURRENCY,
      Constants.FIAT_USD
    )!!.toUpperCase()
  }

  private const val NO_FIAT_RATE = "--"

  /**
   * Converts bitcoin amount to the fiat currency preferred by the user.
   *
   * @param amount amount to convert
   * @param fiatCode   fiat currency code (USD, EUR, RUB, JPY, ...)
   * @return localized formatted string of the converted amount
   */
  @JvmStatic
  fun convertMsatToFiat(amount: MilliSatoshi, fiatCode: String?): BigDecimal {
    val rate = App.RATES.get(fiatCode) ?: -1.0f
    return `package$`.`MODULE$`.satoshi2btc(amount.truncateToSatoshi()).toBigDecimal().`$times`(
      BigDecimal.decimal(rate)
    )
  }

  /**
   * Converts fiat amount to bitcoin amount in Msat.
   *
   * @param fiatAmount amount in fiat
   * @param fiatCode   fiat currency code (USD, EUR, RUB, JPY, ...)
   * @return localized formatted string of the converted amount
   */
  @JvmStatic
  fun convertFiatToMsat(fiatAmount: String?, fiatCode: String?): MilliSatoshi {
    val rate = App.RATES.get(fiatCode) ?: -1.0f
    return MilliSatoshi.toMilliSatoshi(
      Btc(
        `BigDecimal$`.`MODULE$`.apply(fiatAmount).`$div`(
          BigDecimal.decimal(rate)
        )
      )
    )
  }

  /**
   * Prints bitcoin amount to the fiat currency preferred by the user. Output is a pretty localized print.
   *
   * @param amount amount to format
   * @param fiatCode   fiat currency code (USD, EUR, RUB, JPY, ...)
   * @return localized formatted string of the converted amount
   */
  @JvmStatic
  fun formatMsatToFiat(amount: MilliSatoshi, fiatCode: String?): String {
    val fiatValue = convertMsatToFiat(amount, fiatCode).toDouble()
    return if (fiatValue < 0) NO_FIAT_RATE else fiatFormat!!.format(fiatValue)
  }

  @JvmStatic
  fun formatMsatToFiatWithUnit(amount: MilliSatoshi, fiatCode: String): String {
    return formatMsatToFiat(amount, fiatCode) + " " + fiatCode.toUpperCase()
  }

  @JvmStatic
  fun formatSatToFiat(amount: Satoshi?, fiatCode: String?): String {
    val rate = App.RATES.get(fiatCode) ?: -1.0f
    return if (rate < 0) NO_FIAT_RATE else fiatFormat!!.format(
      `package$`.`MODULE$`.satoshi2btc(
        amount
      ).toDouble() * rate
    )
  }

  @JvmStatic
  fun formatSatToFiatWithUnit(amount: Satoshi?, fiatCode: String): String {
    return formatSatToFiat(amount, fiatCode) + " " + fiatCode.toUpperCase()
  }

  @JvmStatic
  fun getPreferredCoinUnit(prefs: SharedPreferences): CoinUnit {
    return CoinUtils.getUnitFromString(
      prefs.getString(
        Constants.SETTING_BTC_UNIT,
        Constants.BTC_CODE
      )
    )
  }

  /**
   * Prints a stringified amount in a text view. Decimal part if present is smaller than int part.
   */
  @JvmStatic
  @SuppressLint("SetTextI18n")
  fun printAmountInView(view: TextView, amount: String, direction: String) {
    val amountParts = amount.split(Pattern.quote(DECIMAL_SEPARATOR)).toTypedArray()
    if (amountParts.size == 2) {
      view.text = Html.fromHtml(
        view.context.getString(
          R.string.pretty_amount_value,
          direction + amountParts[0] + DECIMAL_SEPARATOR,
          amountParts[1]
        )
      )
    } else {
      view.text = direction + amount
    }
  }

  @JvmStatic
  fun printAmountInView(view: TextView, amount: String) {
    printAmountInView(view, amount, "")
  }

  /**
   * Return amount as Long, in millisatoshi
   */
  @JvmStatic
  fun getLongAmountFromInvoice(paymentRequest: PaymentRequest): Long {
    return if (paymentRequest.amount().isEmpty) 0 else paymentRequest.amount().get().toLong()
  }

  @JvmStatic
  fun getAmountFromInvoice(paymentRequest: PaymentRequest): MilliSatoshi {
    return if (paymentRequest.amount().isEmpty) MilliSatoshi(0) else paymentRequest.amount().get()
  }

  @JvmStatic
  val chainHash: ByteVector32
    get() = if ("mainnet" == BuildConfig.CHAIN) Block.LivenetGenesisBlock()
      .hash() else Block.TestnetGenesisBlock().hash()

  @JvmStatic
  fun getDatadir(context: Context): File {
    return File(context.filesDir, Constants.ECLAIR_DATADIR)
  }

  @JvmStatic
  fun getChainDatadir(context: Context): File {
    return File(getDatadir(context), BuildConfig.CHAIN)
  }

  @JvmStatic
  fun getWalletDBFile(context: Context): File {
    return File(getChainDatadir(context), Constants.WALLET_DB_FILE)
  }

  @JvmStatic
  fun getNetworkDBFile(context: Context): File {
    return File(getChainDatadir(context), Constants.NETWORK_DB_FILE)
  }

  @JvmStatic
  fun getEclairDBFile(context: Context): File {
    return File(getChainDatadir(context), Constants.ECLAIR_DB_FILE)
  }

  /**
   * Retrieve the actual eclair backup file created by eclair core. This is the file that should be backed up.
   */
  @JvmStatic
  fun getEclairDBFileBak(context: Context): File {
    return File(getChainDatadir(context), Constants.ECLAIR_DB_FILE_BAK)
  }

  @JvmStatic
  fun getEclairBackupFileName(seedHash: String): String {
    return "eclair_" + BuildConfig.CHAIN + "_" + seedHash + ".bkup"
  }

  @JvmStatic
  fun toAscii(b: ByteVector): String {
    val bytes = b.toArray()
    return String(bytes, StandardCharsets.US_ASCII)
  }

  @JvmStatic
  fun getDeviceId(context: Context): String {
    return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
  }

  @JvmStatic
  fun setupLogging(context: Context) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    when (prefs.getString(Constants.SETTING_LOGS_OUTPUT, Constants.LOGS_OUTPUT_LOCAL)) {
      Constants.LOGS_OUTPUT_NONE -> disableLogging()
      else -> try {
        setupLocalLogging(context)
      } catch (e: ExternalStorageUnavailableException) {
        Log.e("WalletUtils", "external storage is not available, cannot enable local logging")
      }
    }
    if (!prefs.contains(Constants.SETTING_LOGS_OUTPUT)) {
      prefs.edit().putString(Constants.SETTING_LOGS_OUTPUT, Constants.LOGS_OUTPUT_LOCAL).apply()
    }
  }

  @JvmStatic
  fun disableLogging() {
    val lc = LoggerFactory.getILoggerFactory() as LoggerContext
    lc.stop()
  }

  private fun getLogcatAppender(lc: LoggerContext): LogcatAppender {
    val tagEncoder = PatternLayoutEncoder()
    tagEncoder.context = lc
    tagEncoder.pattern = "%logger{12}"
    tagEncoder.start()
    val encoder = PatternLayoutEncoder()
    encoder.context = lc
    encoder.pattern = "%X{nodeId}%X{channelId} - %msg%ex{24}%n"
    encoder.start()
    val logcatAppender = LogcatAppender()
    logcatAppender.context = lc
    logcatAppender.encoder = encoder
    logcatAppender.tagEncoder = tagEncoder
    logcatAppender.start()
    return logcatAppender
  }

  /**
   * Sets up an index-based rolling policy with a max file size of 4MB.
   */
  @JvmStatic
  @Throws(ExternalStorageUnavailableException::class)
  fun setupLocalLogging(context: Context) {
    if (Environment.MEDIA_MOUNTED != Environment.getExternalStorageState()) {
      throw ExternalStorageUnavailableException()
    }
    val lc = LoggerFactory.getILoggerFactory() as LoggerContext
    lc.reset()
    val logsDir = context.getExternalFilesDir(Constants.LOGS_DIR)
    if (!logsDir!!.exists()) logsDir.mkdirs()
    val encoder = PatternLayoutEncoder()
    encoder.context = lc
    encoder.pattern = Constants.ENCODER_PATTERN
    encoder.start()
    val rollingFileAppender = RollingFileAppender<ILoggingEvent>()
    rollingFileAppender.context = lc
    rollingFileAppender.file = File(logsDir, Constants.CURRENT_LOG_FILE).absolutePath
    val rollingPolicy = FixedWindowRollingPolicy()
    rollingPolicy.context = lc
    rollingPolicy.setParent(rollingFileAppender)
    rollingPolicy.minIndex = 1
    rollingPolicy.maxIndex = 2
    rollingPolicy.fileNamePattern = File(logsDir, Constants.ARCHIVED_LOG_FILE).absolutePath
    rollingPolicy.start()
    val triggeringPolicy = SizeBasedTriggeringPolicy<ILoggingEvent>()
    triggeringPolicy.context = lc
    triggeringPolicy.maxFileSize = FileSize.valueOf("4mb")
    triggeringPolicy.start()
    rollingFileAppender.encoder = encoder
    rollingFileAppender.rollingPolicy = rollingPolicy
    rollingFileAppender.triggeringPolicy = triggeringPolicy
    rollingFileAppender.start()
    useAppender(lc, rollingFileAppender)
  }

  private fun useAppender(lc: LoggerContext, appender: Appender<ILoggingEvent>) {
    // filter some classes
    lc.getLogger("fr.acinq.eclair.crypto").level =
      Level.WARN // ChaCha20Poly1305 spams a lot in debug
    lc.getLogger("fr.acinq.eclair.payment.BalanceEventThrottler").level = Level.WARN
    lc.getLogger("fr.acinq.eclair.db.BackupHandler").level = Level.WARN
    if (BuildConfig.DEBUG) {
      lc.getLogger("io.netty").level = Level.DEBUG
    } else {
      lc.getLogger("io.netty").level = Level.WARN
    }

    // add requested appender
    val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
    root.level =
      if (BuildConfig.DEBUG) Level.DEBUG else Level.INFO
    root.addAppender(appender)

    // add logcat if debug
    if (BuildConfig.DEBUG) {
      root.addAppender(getLogcatAppender(lc))
    }
  }

  @JvmStatic
  fun getLastLocalLogFileUri(context: Context): Uri? {
    val logsDir = context.getExternalFilesDir(Constants.LOGS_DIR)
    if (logsDir != null && !logsDir.exists()) {
      logsDir.mkdirs()
    }
    val logFile = File(logsDir, Constants.CURRENT_LOG_FILE)
    return if (logFile.exists()) {
      try {
        FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", logFile)
      } catch (e: Exception) {
        log.error("could not open local log file: ", e)
        null
      }
    } else {
      null
    }
  }

  /**
   * Builds a TypeSafe configuration to override the default conf of the node setup. Returns an empty config if no configuration entry must be overridden.
   *
   *
   * If the user has set a preferred electrum server, retrieves it from the prefs and adds it to the configuration.
   */
  @JvmStatic
  fun getOverrideConfig(prefs: SharedPreferences): Config {
    val prefsElectrumAddress = prefs.getString(Constants.CUSTOM_ELECTRUM_SERVER, "")!!
      .trim { it <= ' ' }
    if (!Strings.isNullOrEmpty(prefsElectrumAddress)) {
      try {
        val address = HostAndPort.fromString(prefsElectrumAddress).withDefaultPort(50002)
        val conf: MutableMap<String, Any> = HashMap()
        if (!Strings.isNullOrEmpty(address.host)) {
          conf["eclair.electrum.host"] = address.host
          conf["eclair.electrum.port"] = address.port
          if (address.host.endsWith(".onion")) {
            // If Tor is used, we don't require TLS; Tor already adds a layer of encryption.
            conf["eclair.electrum.ssl"] = "off"
          } else {
            // Otherwise we require TLS with a valid server certificate.
            conf["eclair.electrum.ssl"] = "strict"
          }
          return ConfigFactory.parseMap(conf)
        }
      } catch (e: Exception) {
        log.error("could not read custom electrum address=$prefsElectrumAddress", e)
      }
    }
    return ConfigFactory.empty()
  }

  /**
   * Retrieve blockheight from context, using eclair appkit in App.
   *
   * @return blockheight long, 0 if there was a problem and appkit is not available.
   */
  @JvmStatic
  fun getBlockHeight(context: Context): Long {
    return try {
      (context as App).appKit.eclairKit.nodeParams().currentBlockHeight()
    } catch (t: Throwable) {
      log.info("could not retrieve blockheight from app context")
      0
    }
  }
}
