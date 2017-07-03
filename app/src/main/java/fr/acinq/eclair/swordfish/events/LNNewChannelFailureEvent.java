package fr.acinq.eclair.swordfish.events;

public class LNNewChannelFailureEvent {
  public final String cause;

  public LNNewChannelFailureEvent(String cause) {
    this.cause = cause;
  }
}
