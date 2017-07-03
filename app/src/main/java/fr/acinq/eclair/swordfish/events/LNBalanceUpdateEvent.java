package fr.acinq.eclair.swordfish.events;

import fr.acinq.bitcoin.MilliSatoshi;

public class LNBalanceUpdateEvent {
  public final long availableBalanceMsat;
  public final long pendingBalanceMsat;
  public final long offlineBalanceMsat;

  public LNBalanceUpdateEvent(long availableBalanceMsat, long pendingBalanceMsat, long offlineBalanceMsat) {
    this.availableBalanceMsat = availableBalanceMsat;
    this.pendingBalanceMsat = pendingBalanceMsat;
    this.offlineBalanceMsat = offlineBalanceMsat;
  }

  public MilliSatoshi total() {
    return new MilliSatoshi(availableBalanceMsat + pendingBalanceMsat + offlineBalanceMsat);
  }
}
