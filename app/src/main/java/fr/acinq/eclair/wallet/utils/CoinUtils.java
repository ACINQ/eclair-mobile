package fr.acinq.eclair.wallet.utils;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.payment.PaymentRequest;

public class CoinUtils {
  public final static String BTC_PATTERN = "###,##0.000#####";
  public final static String MILLI_BTC_PATTERN = "###,##0.00";
  private static DecimalFormat btcFormat;
  private static DecimalFormat milliBtcFormat;
  private static NumberFormat fiatFormat;

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

  public static String getMilliBtcAmountFromInvoice(PaymentRequest paymentRequest, boolean withUnit) {
    return formatAmountMilliBtc(getAmountFromInvoice(paymentRequest)) + (withUnit ? " mBTC" : "");
  }

  public static String formatAmountMilliBtc(MilliSatoshi amount) {
    return CoinUtils.getMilliBTCFormat().format(package$.MODULE$.millisatoshi2millibtc(amount).amount());
  }
  public static String formatAmountBtc(MilliSatoshi amount) {
    return CoinUtils.getBTCFormat().format(package$.MODULE$.millisatoshi2btc(amount).amount());
  }

  public static Satoshi parseBitcoinAmout(String input) {
    long amount = new BigDecimal(input).movePointRight(8).longValueExact();
    return new Satoshi(amount);
  }

  public static Satoshi parseMilliSatoshiAmout(String input) {
    long amount = new BigDecimal(input).movePointRight(8).divide(BigDecimal.valueOf(1000)).longValueExact();
    return new Satoshi(amount);
  }
}
