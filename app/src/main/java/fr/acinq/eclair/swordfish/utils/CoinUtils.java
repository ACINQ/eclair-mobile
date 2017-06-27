package fr.acinq.eclair.swordfish.utils;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.payment.PaymentRequest;

public class CoinUtils {
  public final static String BTC_PATTERN = "###,##0.000#####";
  public final static String MILLI_BTC_PATTERN = "###,##0.00";
  private static DecimalFormat btcFormat;
  private static DecimalFormat milliBtcFormat;

  private CoinUtils() {
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
    return CoinUtils.getMilliBTCFormat().format(package$.MODULE$.millisatoshi2millibtc(amount));
  }
}
