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

  public static String getBtcPreferredUnit(final SharedPreferences prefs) {
    return prefs.getString(Constants.SETTING_BTC_UNIT, Constants.MILLI_BTC_CODE);
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
        case Constants.MILLI_BTC_CODE:
          return getMilliBTCFormat().format(package$.MODULE$.millisatoshi2millibtc(amountMsat).amount());
        case Constants.BTC_CODE:
          return getBTCFormat().format(package$.MODULE$.millisatoshi2btc(amountMsat).amount());
      }
    } else if (amount instanceof Satoshi) {
      final Satoshi amountSat = (Satoshi) amount;
      switch (unit) {
        case MILLI_SATOSHI_CODE:
          return satoshiFormat.format(package$.MODULE$.satoshi2millisatoshi(amountSat).amount());
        case SATOSHI_CODE:
          return satoshiFormat.format(amountSat.amount());
        case Constants.MILLI_BTC_CODE:
          return getMilliBTCFormat().format(package$.MODULE$.satoshi2millibtc(amountSat).amount());
        case Constants.BTC_CODE:
          return getBTCFormat().format(package$.MODULE$.satoshi2btc(amountSat).amount());
      }
    } else if (amount instanceof MilliBtc) {
      final MilliBtc amountMbtc = (MilliBtc) amount;
      switch (unit) {
        case MILLI_SATOSHI_CODE:
          return satoshiFormat.format(package$.MODULE$.millibtc2millisatoshi(amountMbtc).amount());
        case SATOSHI_CODE:
          return satoshiFormat.format(package$.MODULE$.millibtc2satoshi(amountMbtc).amount());
        case Constants.MILLI_BTC_CODE:
          return getMilliBTCFormat().format(amountMbtc.amount());
        case Constants.BTC_CODE:
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
        case Constants.MILLI_BTC_CODE:
          return getMilliBTCFormat().format(package$.MODULE$.btc2millibtc(amountBtc).amount());
        case Constants.BTC_CODE:
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

  public static Satoshi parseBitcoinAmount(String input) {
    long amount = new BigDecimal(input).movePointRight(8).longValueExact();
    return new Satoshi(amount);
  }

  public static Satoshi parseMilliSatoshiAmount(String input) {
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
      case Constants.MILLI_BTC_CODE:
        return "mBTC";
      case Constants.BTC_CODE:
        return "BTC";
      default:
    }
    return code;
  }
}
