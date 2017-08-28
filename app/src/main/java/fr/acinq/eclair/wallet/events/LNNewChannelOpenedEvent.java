package fr.acinq.eclair.wallet.events;

public class LNNewChannelOpenedEvent {
  public final String targetNode;

  public LNNewChannelOpenedEvent(String targetNode) {
    this.targetNode = targetNode;
  }
}
