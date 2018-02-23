package fr.acinq.eclair.wallet.utils;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.*;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.wallet.App;


public class WalletUtils {
  private static final String TAG = "WalletUtils";
  public final static List<String> LN_NODES = Arrays.asList(
    "03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134@endurance.acinq.co:9735"
  );
  private static NumberFormat fiatFormat;

  public static View.OnClickListener getOpenTxListener(final String txId) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        final String uri = PreferenceManager.getDefaultSharedPreferences(v.getContext())
          .getString(Constants.SETTING_ONCHAIN_EXPLORER, "https://api.blockcypher.com/v1/btc/test3/txs/");
        try {
          Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri + txId));
          v.getContext().startActivity(browserIntent);
        } catch (Throwable t) {
          Log.e(WalletUtils.class.getSimpleName(), "Could not open explorer with uri=" + uri + txId);
          Toast.makeText(v.getContext(), "Could not open explorer", Toast.LENGTH_SHORT).show();
        }
      }
    };
  }

  /**
   * Reads a list of words from the mnemonics file. This is a placeholder implementation, waiting for proper implementation in eclair core.
   * @param datadir
   * @return
   * @throws IOException
   */
  public static List<String> readMnemonicsFile(final File datadir) throws IOException{
    try {
      return Files.readLines(new File(datadir, "mnemonics.dat"), Charset.defaultCharset());
    } catch (IOException e) {
      Log.e(TAG, "Could not read mnemonics file");
      throw e;
    }
  }

  /**
   * Writes a list of words into the mnemonics file. This is a placeholder implementation, waiting for proper implementation in eclair core.
   * @param datadir
   * @param words
   * @throws IOException
   */
  public static void writeMnemonicsFile(final File datadir, final List<String> words) throws IOException {
    try {
      if (!datadir.exists()) {
        datadir.mkdir();
      }
      Files.asCharSink(new File(datadir, "mnemonics.dat"), Charset.defaultCharset(), FileWriteMode.APPEND).writeLines(words);
    } catch (SecurityException e) {
      Log.e(TAG, "Could not create datadir", e);
      throw e;
    } catch (IOException e) {
      Log.e(TAG, "Could not write mnemonics file", e);
      throw e;
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
   * Gets the user's preferred fiat currency. Defaults is USD
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
