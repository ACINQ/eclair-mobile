package fr.acinq.eclair.wallet.utils;

import java.util.regex.Pattern;

/**
 * Created by Dominique on 07/06/2017.
 */

public class Validators {
  public static final long MAX_FUNDING_MSAT = 16777216L * 1000; // 2^24 satoshis = 167 mBTC
  public static final long MIN_FUNDING_MSAT = 100000L * 1000; // 1 mBTC
  public static final long MIN_LEFTOVER_ONCHAIN_BALANCE_SAT = 100000L; // minimal amount that should stay onchain to handle fees
  public static final Pattern HOST_REGEX = Pattern.compile("([a-fA-F0-9]{66})@([a-zA-Z0-9:\\.\\-_]+)(:([0-9]+))?");
}
