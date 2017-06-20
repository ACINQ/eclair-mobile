package fr.acinq.eclair.swordfish.events;

public class BalanceUpdateEvent {
  public final long availableBalanceSat;
  public final long pendingBalanceSat;
  public final long offlineBalanceSat;

  public BalanceUpdateEvent(long availableBalanceSat, long pendingBalanceSat, long offlineBalanceSat) {
    this.availableBalanceSat = availableBalanceSat;
    this.pendingBalanceSat = pendingBalanceSat;
    this.offlineBalanceSat = offlineBalanceSat;
  }
}
