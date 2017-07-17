package fr.acinq.eclair.wallet.events;

import fr.acinq.bitcoin.MilliSatoshi;

public class LNBalanceUpdateEvent {
  public final long availableBalanceMsat;
  public final long pendingBalanceMsat;
  public final long offlineBalanceMsat;
  public final long closingBalanceMsat;

  public LNBalanceUpdateEvent(long availableBalanceMsat, long pendingBalanceMsat, long offlineBalanceMsat, long closingBalanceMsat) {
    this.availableBalanceMsat = availableBalanceMsat;
    this.pendingBalanceMsat = pendingBalanceMsat;
    this.offlineBalanceMsat = offlineBalanceMsat;
    this.closingBalanceMsat = closingBalanceMsat;
  }

  public MilliSatoshi total() {
    // ignoring closing balance
    return new MilliSatoshi(availableBalanceMsat + pendingBalanceMsat + offlineBalanceMsat);
  }
}
