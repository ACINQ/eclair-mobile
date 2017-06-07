package fr.acinq.eclair.swordfish.utils;

import java.util.regex.Pattern;

/**
 * Created by Dominique on 07/06/2017.
 */

public class Validators {
  public static final long MAX_FUNDING_SAT = 16777216L; // 2^24 satoshis = 167 mBTC
  public static final long MIN_FUNDING_SAT = 10000L; // 0.1 mBTC
  public static final Pattern HOST_REGEX = Pattern.compile("([a-fA-F0-9]{66})@([a-zA-Z0-9:\\.\\-_]+):([0-9]+)");
}
