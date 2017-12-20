package fr.acinq.eclair.wallet.utils;

import android.content.SharedPreferences;
import android.util.Log;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import fr.acinq.bitcoin.Btc;
import fr.acinq.bitcoin.BtcAmount;
import fr.acinq.bitcoin.MilliBtc;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.wallet.App;

import static fr.acinq.eclair.wallet.utils.Constants.BITS_CODE;
import static fr.acinq.eclair.wallet.utils.Constants.BTC_CODE;
import static fr.acinq.eclair.wallet.utils.Constants.MILLI_BTC_CODE;
import static fr.acinq.eclair.wallet.utils.Constants.MILLI_SATOSHI_CODE;
import static fr.acinq.eclair.wallet.utils.Constants.SATOSHI_CODE;

public class CoinUtils {

  public final static String BTC_PATTERN = "###,##0.000#####";
  public final static String MILLI_BTC_PATTERN = "###,##0.00###";
  private static final String TAG = "CoinUtils";
  private static DecimalFormat btcFormat;
  private static DecimalFormat milliBtcFormat;
  private static NumberFormat fiatFormat;
  private static NumberFormat satoshiFormat = NumberFormat.getInstance();

  private CoinUtils() {
  }

  public static NumberFormat getFiatFormat() {
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
   * @param prefs
   * @return
   */
  public static String getPreferredFiat(final SharedPreferences prefs) {
    return prefs.getString(Constants.SETTING_SELECTED_FIAT_CURRENCY, Constants.FIAT_USD);
  }

  /**
   * Converts bitcoin amount to the fiat in parameter. Defaults to USD if the fiat code is unknown
   *
   * @param amountMsat amount in milli satoshis
   * @param fiatCurrency Fiat currency code, EUR/USD, defaults to USD
   * @return localized formatted string of the converted amount
   */
  public static String convertMsatToFiat(final long amountMsat, final String fiatCurrency) {
    final double rate = Constants.FIAT_EURO.equals(fiatCurrency) ? App.getEurRate() : App.getUsdRate();
    if (rate <= 0) return "N/A";
    return CoinUtils.getFiatFormat().format(package$.MODULE$.millisatoshi2btc(new MilliSatoshi(amountMsat)).amount().doubleValue() * rate);
  }

  public static String convertMsatToFiatWithUnit(final long amountMsat, final String fiatCurrency) {
    return CoinUtils.convertMsatToFiat(amountMsat, fiatCurrency) + " " + fiatCurrency.toUpperCase();
  }

  public static String getBtcPreferredUnit(final SharedPreferences prefs) {
    return prefs.getString(Constants.SETTING_BTC_UNIT, MILLI_BTC_CODE);
  }

  /**
   * Returns a localized formatted string representing the bitcoin amount converted to the btc unit,
   * concatenated with the formatted short unit label.
   *
   * @param amount Bitcoin amount, should be {@link MilliSatoshi}, {@link Satoshi}, {@link MilliBtc} or {@link Btc}
   * @param unit   unit to convert amount to
   * @return formatted String
   */
  public static String formatAmountInUnitWithUnit(final BtcAmount amount, final String unit) {
    return CoinUtils.formatAmountInUnit(amount, unit) + " " + CoinUtils.getBitcoinUnitShortLabel(unit);
  }

  /**
   * Returns a localized formatted string representing the bitcoin amount converted to the btc unit
   * set in the user preferences.
   *
   * @param amount Bitcoin amount, should be {@link MilliSatoshi}, {@link Satoshi}, {@link MilliBtc} or {@link Btc}
   * @param prefs  user default preferences
   * @return formatted String
   */
  public static String formatAmountInUnit(final BtcAmount amount, final SharedPreferences prefs) {
    return formatAmountInUnit(amount, getBtcPreferredUnit(prefs));
  }

  /**
   * Returns a localized formatted string representing the bitcoin amount converted to the btc unit.
   *
   * @param amount Bitcoin amount, should be {@link MilliSatoshi}, {@link Satoshi}, {@link MilliBtc} or {@link Btc}
   * @param unit   unit to convert amount to
   * @return formatted String
   */
  public static String formatAmountInUnit(final BtcAmount amount, final String unit) {
    if (amount == null) return "Unreadable";

    // ------ MilliSatoshi => prefUnit
    if (amount instanceof MilliSatoshi) {
      final MilliSatoshi amountMsat = (MilliSatoshi) amount;
      switch (unit) {
        case MILLI_SATOSHI_CODE:
          return satoshiFormat.format(amountMsat.amount());
        case SATOSHI_CODE:
          return satoshiFormat.format(package$.MODULE$.millisatoshi2satoshi(amountMsat).amount());
        case BITS_CODE:
          return getFiatFormat().format(package$.MODULE$.millisatoshi2millibtc(amountMsat).amount().$times(scala.math.BigDecimal.exact(1000L)));
        case MILLI_BTC_CODE:
          return getMilliBTCFormat().format(package$.MODULE$.millisatoshi2millibtc(amountMsat).amount());
        case BTC_CODE:
          return getBTCFormat().format(package$.MODULE$.millisatoshi2btc(amountMsat).amount());
      }
    } else if (amount instanceof Satoshi) {
      final Satoshi amountSat = (Satoshi) amount;
      switch (unit) {
        case MILLI_SATOSHI_CODE:
          return satoshiFormat.format(package$.MODULE$.satoshi2millisatoshi(amountSat).amount());
        case SATOSHI_CODE:
          return satoshiFormat.format(amountSat.amount());
        case BITS_CODE:
          return getFiatFormat().format(package$.MODULE$.satoshi2millibtc(amountSat).amount().$times(scala.math.BigDecimal.exact(1000L)));
        case MILLI_BTC_CODE:
          return getMilliBTCFormat().format(package$.MODULE$.satoshi2millibtc(amountSat).amount());
        case BTC_CODE:
          return getBTCFormat().format(package$.MODULE$.satoshi2btc(amountSat).amount());
      }
    } else if (amount instanceof MilliBtc) {
      final MilliBtc amountMbtc = (MilliBtc) amount;
      switch (unit) {
        case MILLI_SATOSHI_CODE:
          return satoshiFormat.format(package$.MODULE$.millibtc2millisatoshi(amountMbtc).amount());
        case SATOSHI_CODE:
          return satoshiFormat.format(package$.MODULE$.millibtc2satoshi(amountMbtc).amount());
        case BITS_CODE:
          return getFiatFormat().format(amountMbtc.amount().$times(scala.math.BigDecimal.exact(1000L)));
        case MILLI_BTC_CODE:
          return getMilliBTCFormat().format(amountMbtc.amount());
        case BTC_CODE:
          return getBTCFormat().format(package$.MODULE$.millibtc2btc(amountMbtc).amount());
      }
    }
    if (amount instanceof Btc) {
      final Btc amountBtc = (Btc) amount;
      switch (unit) {
        case MILLI_SATOSHI_CODE:
          return satoshiFormat.format(package$.MODULE$.btc2millisatoshi(amountBtc).amount());
        case SATOSHI_CODE:
          return satoshiFormat.format(package$.MODULE$.btc2satoshi(amountBtc).amount());
        case BITS_CODE:
          return getFiatFormat().format(package$.MODULE$.btc2millibtc(amountBtc).amount().$times(scala.math.BigDecimal.exact(1000L)));
        case MILLI_BTC_CODE:
          return getMilliBTCFormat().format(package$.MODULE$.btc2millibtc(amountBtc).amount());
        case BTC_CODE:
          return getBTCFormat().format(amountBtc.amount());
      }
    } else {
      Log.w(TAG, "Unknown type of amount: " + amount);
    }
    Log.w(TAG, "Amount was not formatted! preferred unit is: " + unit);
    return satoshiFormat.format(amount.toString());
  }

  public static DecimalFormat getBTCFormat() {
    if (btcFormat == null) {
      btcFormat = (DecimalFormat) NumberFormat.getInstance();
      btcFormat.applyPattern(BTC_PATTERN);
    }
    return btcFormat;
  }

  public static DecimalFormat getMilliBTCFormat() {
    if (milliBtcFormat == null) {
      milliBtcFormat = (DecimalFormat) NumberFormat.getInstance();
      milliBtcFormat.applyPattern(MILLI_BTC_PATTERN);
    }
    return milliBtcFormat;
  }

  /**
   * Reads a numeric String, parses the amount and converts the amount from Bitcoin to {@link MilliSatoshi}.
   *
   * @param amountBitcoin must be numeric
   * @return
   * @throws NumberFormatException if the string is not numeric
   */
  public static MilliSatoshi parseBitcoinStringToMsat(final String amountBitcoin) throws NumberFormatException {
    return new MilliSatoshi(new BigDecimal(amountBitcoin).movePointRight(8 + 3).longValueExact());
  }

  /**
   * Reads a numeric String, parses the amount and converts the amount from MilliBitcoin to {@link MilliSatoshi}.
   *
   * @param amountMilliBitcoin must be numeric
   * @return
   * @throws NumberFormatException if the string is not numeric
   */
  public static MilliSatoshi parseMilliBitcoinStringToMsat(final String amountMilliBitcoin) throws NumberFormatException {
    return new MilliSatoshi(new BigDecimal(amountMilliBitcoin).movePointRight(5 + 3).longValueExact());
  }

  /**
   * Reads a numeric String, parses the amount and converts the amount from Bits to {@link MilliSatoshi}.
   *
   * @param amountBits must be numeric
   * @return
   * @throws NumberFormatException if the string is not numeric
   */
  public static MilliSatoshi parseBitsStringToMsat(final String amountBits) throws NumberFormatException {
    return new MilliSatoshi(new BigDecimal(amountBits).movePointRight(2 + 3).longValueExact());
  }

  /**
   * Reads a numeric String, parses the amount and converts the amount from Satoshi to {@link MilliSatoshi}.
   *
   * @param amountSatoshi must be numeric
   * @return
   * @throws NumberFormatException if the string is not numeric
   */
  public static MilliSatoshi parseSatoshiStringToMsat(final String amountSatoshi) throws NumberFormatException {
    return new MilliSatoshi(new BigDecimal(amountSatoshi).movePointRight(3).longValueExact());
  }

  /**
   * Reads a numeric String, parses the amount and converts the amount from MilliSatoshi to {@link MilliSatoshi}.
   *
   * @param amountMilliSatoshi must be numeric
   * @return
   * @throws NumberFormatException if the string is not numeric
   */
  public static MilliSatoshi parseMilliSatoshiStringToMsat(final String amountMilliSatoshi) throws NumberFormatException {
    return new MilliSatoshi(new BigDecimal(amountMilliSatoshi).longValueExact());
  }

  /**
   * Reads a numeric String, parses the amount and converts the amount from the specified unit to {@link MilliSatoshi}.
   *
   * @param amountInUnit Numeric string representing a bitcoin amount in the unit in parameter
   * @param unit unit code of the amount, must be the code corresponding to {@link MilliSatoshi}, {@link Satoshi}, {@link MilliBtc} or {@link Btc}
   * @return the amount in MilliSatoshi
   * @throws NumberFormatException if the amount is not a numeric String
   * @throws IllegalArgumentException if the unit is not known
   */
  public static MilliSatoshi parseStringToMsat(final String amountInUnit, final String unit) throws NumberFormatException, IllegalArgumentException {
    switch (unit) {
      case MILLI_SATOSHI_CODE:
        return parseMilliSatoshiStringToMsat(amountInUnit);
      case SATOSHI_CODE:
        return parseSatoshiStringToMsat(amountInUnit);
      case BITS_CODE:
        return parseBitsStringToMsat(amountInUnit);
      case MILLI_BTC_CODE:
        return parseMilliBitcoinStringToMsat(amountInUnit);
      case BTC_CODE:
        return parseBitcoinStringToMsat(amountInUnit);
      default:
        throw new IllegalArgumentException("Unknown unit");
    }
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

  public static String formatAmountMilliSatoshi(MilliSatoshi amount) {
    return CoinUtils.satoshiFormat.format(amount.amount());
  }

  public static String formatAmountSatoshi(MilliSatoshi amount) {
    return CoinUtils.satoshiFormat.format(package$.MODULE$.millisatoshi2satoshi(amount).amount());
  }

  public static String formatAmountMilliBtc(MilliSatoshi amount) {
    return CoinUtils.getMilliBTCFormat().format(package$.MODULE$.millisatoshi2millibtc(amount).amount());
  }

  public static String formatAmountBtc(MilliSatoshi amount) {
    return CoinUtils.getBTCFormat().format(package$.MODULE$.millisatoshi2btc(amount).amount());
  }

  public static Satoshi parseBitcoinToSatoshi(String input) {
    long amount = new BigDecimal(input).movePointRight(8).longValueExact();
    return new Satoshi(amount);
  }

  public static Satoshi parseMilliBtcToSatoshi(String input) {
    long amount = new BigDecimal(input).movePointRight(8).divide(BigDecimal.valueOf(1000)).longValueExact();
    return new Satoshi(amount);
  }

  /**
   * Get the short label of a bitcoin unit
   *
   * @param code
   * @return
   */
  public static String getBitcoinUnitShortLabel(final String code) {
    switch (code) {
      case Constants.MILLI_SATOSHI_CODE:
        return "mSat";
      case MILLI_BTC_CODE:
        return "mBTC";
      case BTC_CODE:
        return "BTC";
      default:
    }
    return code;
  }
}
