package fr.acinq.eclair.swordfish.events;

public class BalanceUpdateEvent {
  public final long availableBalanceMsat;
  public final long pendingBalanceMsat;
  public final long offlineBalanceMsat;

  public BalanceUpdateEvent(long availableBalanceMsat, long pendingBalanceMsat, long offlineBalanceMsat) {
    this.availableBalanceMsat = availableBalanceMsat;
    this.pendingBalanceMsat = pendingBalanceMsat;
    this.offlineBalanceMsat = offlineBalanceMsat;
  }
}
