package fr.acinq.eclair.swordfish.utils;

import java.text.DecimalFormat;
import java.text.NumberFormat;

public class CoinFormat {
  private CoinFormat() {
  }

  public final static String BTC_PATTERN = "###,##0.000#####";
  public final static String MILLI_BTC_PATTERN = "###,##0.00";
  private static DecimalFormat btcFormat;
  private static DecimalFormat milliBtcFormat;

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
}
