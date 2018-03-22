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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.wallet.App;


public class WalletUtils {
  public final static List<String> LN_NODES = Arrays.asList(
    "03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134@endurance.acinq.co:9735"
  );
  private static final String TAG = "WalletUtils";
  private static final int VERSION_LENGTH = 1;
  private static final int SALT_LENGTH_V1 = 128;
  private static final int IV_LENGTH_V1 = 16;
  private static final int MAC_LENGTH_V1 = 32;
  private final static byte SEED_FILE_VERSION_1 = 1;
  public final static String UNENCRYPTED_SEED_NAME = "seed.dat";
  public final static String SEED_NAME = "enc_seed.dat";
  private final static String SEED_NAME_TEMP = "enc_seed_temp.dat";
  private static byte currentSeedFileVersion = SEED_FILE_VERSION_1;
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
    final byte version = fileContent[0];
    if (version == SEED_FILE_VERSION_1) {
      final byte[] salt = readSalt_v1(fileContent);
      final AesCbcWithIntegrity.SecretKeys sk = AesCbcWithIntegrity.generateKeyFromPassword(password, salt);
      final AesCbcWithIntegrity.CipherTextIvMac civ = new AesCbcWithIntegrity.CipherTextIvMac(readCipher_v1(fileContent), readIV_v1(fileContent), readMAC_v1(fileContent));
      return AesCbcWithIntegrity.decrypt(civ, sk);
    } else {
      throw new RuntimeException("unhandled encrypted seed file version");
    }
  }

  public static byte[] readSeedFile(final File datadir, final String password) throws IOException, IllegalAccessException, GeneralSecurityException {
    return readSeedFile(datadir, SEED_NAME, password);
  }

  private static byte[] readSalt_v1(byte[] content) {
    final byte[] salt = new byte[SALT_LENGTH_V1];
    System.arraycopy(content, VERSION_LENGTH, salt, 0, SALT_LENGTH_V1);
    return salt;
  }

  private static byte[] readIV_v1(byte[] content) {
    final byte[] iv = new byte[IV_LENGTH_V1];
    System.arraycopy(content, VERSION_LENGTH + SALT_LENGTH_V1, iv, 0, IV_LENGTH_V1);
    return iv;
  }

  private static byte[] readMAC_v1(byte[] content) {
    final byte[] mac = new byte[MAC_LENGTH_V1];
    System.arraycopy(content, VERSION_LENGTH + SALT_LENGTH_V1 + IV_LENGTH_V1, mac, 0, MAC_LENGTH_V1);
    return mac;
  }

  private static byte[] readCipher_v1(byte[] content) {
    final int cipherLength = content.length - VERSION_LENGTH - SALT_LENGTH_V1 - IV_LENGTH_V1 - MAC_LENGTH_V1;
    final byte[] cipher = new byte[cipherLength];
    System.arraycopy(content, VERSION_LENGTH + SALT_LENGTH_V1 + IV_LENGTH_V1 + MAC_LENGTH_V1, cipher, 0, cipherLength);
    return cipher;
  }

  public static void writeSeedFile(final File datadir, final byte[] seed, final String password) throws IOException {
    try {
      if (!datadir.exists()) {
        datadir.mkdir();
      }
      final byte[] salt = AesCbcWithIntegrity.generateSalt();
      final AesCbcWithIntegrity.SecretKeys sk = AesCbcWithIntegrity.generateKeyFromPassword(password, salt);
      final AesCbcWithIntegrity.CipherTextIvMac civ = AesCbcWithIntegrity.encrypt(seed, sk);
      if (currentSeedFileVersion == SEED_FILE_VERSION_1) {
        if (salt.length != SALT_LENGTH_V1 || civ.getIv().length != IV_LENGTH_V1 || civ.getMac().length != MAC_LENGTH_V1) {
          throw new Exception();
        }
      }
      final ByteArrayOutputStream fileContent = new ByteArrayOutputStream();
      fileContent.write(currentSeedFileVersion);
      fileContent.write(salt);
      fileContent.write(civ.getIv());
      fileContent.write(civ.getMac());
      fileContent.write(civ.getCipherText());
      final File temp = new File(datadir, SEED_NAME_TEMP);
      Files.write(fileContent.toByteArray(), temp);

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

}
