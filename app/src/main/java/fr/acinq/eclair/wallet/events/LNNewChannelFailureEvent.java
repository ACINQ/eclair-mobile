package fr.acinq.eclair.wallet.events;

public class LNNewChannelFailureEvent {
  public final String cause;

  public LNNewChannelFailureEvent(String cause) {
    this.cause = cause;
  }
}
