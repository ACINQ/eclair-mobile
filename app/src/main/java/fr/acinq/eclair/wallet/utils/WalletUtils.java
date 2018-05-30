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

import com.google.common.io.Files;
import com.tozny.crypto.android.AesCbcWithIntegrity;

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
  public final static String UNENCRYPTED_SEED_NAME = "seed.dat";
  public final static String SEED_NAME = "enc_seed.dat";
  private static final String TAG = "WalletUtils";
  private final static String SEED_NAME_TEMP = "enc_seed_temp.dat";
  private static NumberFormat fiatFormat;

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
      fiatFormat.setMaximumFractionDigits(2);
    }
    return fiatFormat;
  }

  public static boolean shouldDisplayInFiat(final SharedPreferences prefs) {
    return prefs.getBoolean(Constants.SETTING_DISPLAY_IN_FIAT, false);
  }

  /**
   * Gets the user's preferred fiat currency. Default is USD
   *
   * @param prefs
   * @return
   */
  public static String getPreferredFiat(final SharedPreferences prefs) {
    return prefs.getString(Constants.SETTING_SELECTED_FIAT_CURRENCY, Constants.FIAT_USD);
  }

  /**
   * Converts bitcoin amount to the fiat in parameter. Defaults to USD if the fiat code is unknown
   *
   * @param amountMsat   amount in milli satoshis
   * @param fiatCurrency Fiat currency code, EUR/USD, defaults to USD
   * @return localized formatted string of the converted amount
   */
  public static String convertMsatToFiat(final long amountMsat, final String fiatCurrency) {
    final double rate = Constants.FIAT_EURO.equals(fiatCurrency) ? App.getEurRate() : App.getUsdRate();
    if (rate <= 0) return "N/A";
    return getFiatFormat().format(package$.MODULE$.millisatoshi2btc(new MilliSatoshi(amountMsat)).amount().doubleValue() * rate);
  }

  public static String convertMsatToFiatWithUnit(final long amountMsat, final String fiatCurrency) {
    return convertMsatToFiat(amountMsat, fiatCurrency) + " " + fiatCurrency.toUpperCase();
  }

  public static CoinUnit getPreferredCoinUnit(final SharedPreferences prefs) {
    return fr.acinq.eclair.CoinUtils.getUnitFromString(prefs.getString(Constants.SETTING_BTC_UNIT, Constants.BTC_CODE));
  }

  /**
   * Return amount as Long, in millisatoshi
   *
   * @param paymentRequest
   * @return
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
