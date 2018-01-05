package fr.acinq.eclair.wallet.events;

public class ElectrumConnectionEvent {
  public final boolean connected;

  public ElectrumConnectionEvent(boolean connected) {
    this.connected = connected;
  }
}
