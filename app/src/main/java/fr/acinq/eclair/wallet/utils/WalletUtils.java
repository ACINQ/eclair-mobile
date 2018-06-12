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

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.common.io.Files;
import com.tozny.crypto.android.AesCbcWithIntegrity;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.NumberFormat;

import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Block;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.wallet.App;
import fr.acinq.eclair.wallet.BuildConfig;

public class WalletUtils {
  public final static String ACINQ_NODE = "03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134@endurance.acinq.co:9735";
  private final static String PRICE_RATE_API = "https://blockchain.info/fr/ticker";
  public final static String UNENCRYPTED_SEED_NAME = "seed.dat";
  public final static String SEED_NAME = "enc_seed.dat";
  private static final String TAG = "WalletUtils";
  private final static String SEED_NAME_TEMP = "enc_seed_temp.dat";
  private static NumberFormat fiatFormat;

  private static void saveCurrency(final SharedPreferences.Editor editor, final JSONObject o, final String fiatCode) {
    float rate = -1.0f;
    try {
      rate = (float) o.getJSONObject(fiatCode).getDouble("last");
    } catch (Exception e) {
      Log.d(TAG, "could not read " + fiatCode + " from price api response");
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
    retrieveRateFromPrefs(prefs, "CLP");
    retrieveRateFromPrefs(prefs, "USD");
    retrieveRateFromPrefs(prefs, "CNY");
    retrieveRateFromPrefs(prefs, "DKK");
    retrieveRateFromPrefs(prefs, "EUR");
    retrieveRateFromPrefs(prefs, "GBP");
    retrieveRateFromPrefs(prefs, "HKD");
    retrieveRateFromPrefs(prefs, "INR");
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

  public static JsonObjectRequest exchangeRateRequest(final SharedPreferences prefs) {
    return new JsonObjectRequest(Request.Method.GET, PRICE_RATE_API, null,
      response -> {
        final SharedPreferences.Editor editor = prefs.edit();
        saveCurrency(editor, response, "AUD"); // australian dollar
        saveCurrency(editor, response, "BRL"); // br real
        saveCurrency(editor, response, "CHF"); // swiss franc
        saveCurrency(editor, response, "CLP"); // chilean pesos
        saveCurrency(editor, response, "CNY"); // yuan
        saveCurrency(editor, response, "DKK"); // denmark krone
        saveCurrency(editor, response, "EUR"); // euro
        saveCurrency(editor, response, "GBP"); // pound
        saveCurrency(editor, response, "HKD"); // hong kong dollar
        saveCurrency(editor, response, "INR"); // indian rupee
        saveCurrency(editor, response, "JPY"); // yen
        saveCurrency(editor, response, "KRW"); // won
        saveCurrency(editor, response, "NZD"); // nz dollar
        saveCurrency(editor, response, "PLN"); // zloty
        saveCurrency(editor, response, "RUB"); // ruble
        saveCurrency(editor, response, "SEK"); // swedish krona
        saveCurrency(editor, response, "SGD"); // singapore dollar
        saveCurrency(editor, response, "THB"); // thai baht
        saveCurrency(editor, response, "TWD"); // taiwan dollar
        saveCurrency(editor, response, "USD"); // usd
        editor.apply();
      }, (error) -> {
      Log.d(TAG, "error when querying price api api with cause " + error.getMessage());
    });
  }

  public static View.OnClickListener getOpenTxListener(final String txId) {
    return v -> {
      final String uri = PreferenceManager.getDefaultSharedPreferences(v.getContext())
        .getString(Constants.SETTING_ONCHAIN_EXPLORER, "https://api.blockcypher.com/v1/btc/test3/txs/");
      try {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri + txId));
        v.getContext().startActivity(browserIntent);
      } catch (Throwable t) {
        Log.w(WalletUtils.class.getSimpleName(), "Could not open explorer with uri=" + uri + txId);
        Toast.makeText(v.getContext(), "Could not open explorer", Toast.LENGTH_SHORT).show();
      }
    };
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
      fiatFormat.setMaximumFractionDigits(3);
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

  /**
   * Converts bitcoin amount to the fiat currency preferred by the user.
   *
   * @param amountMsat amount in milli satoshis
   * @param fiatCode fiat currency code (USD, EUR, RUB, JPY, ...)
   * @return localized formatted string of the converted amount
   */
  public static String convertMsatToFiat(final long amountMsat, final String fiatCode) {
    final double rate = App.RATES.containsKey(fiatCode) ? App.RATES.get(fiatCode) : -1.0f;
    if (rate < 0) return "--";
    return getFiatFormat().format(package$.MODULE$.millisatoshi2btc(new MilliSatoshi(amountMsat)).amount().doubleValue() * rate);
  }

  public static String convertMsatToFiatWithUnit(final long amountMsat, final String fiatCode) {
    return convertMsatToFiat(amountMsat, fiatCode) + " " + fiatCode.toUpperCase();
  }

  public static CoinUnit getPreferredCoinUnit(final SharedPreferences prefs) {
    return fr.acinq.eclair.CoinUtils.getUnitFromString(prefs.getString(Constants.SETTING_BTC_UNIT, Constants.BTC_CODE));
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

  public static BinaryData getChainHash() {
    return "mainnet".equals(BuildConfig.CHAIN) ? Block.LivenetGenesisBlock().hash() : Block.TestnetGenesisBlock().hash();
  }
}
