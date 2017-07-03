package fr.acinq.eclair.swordfish.events;

public class LNNewChannelOpenedEvent {
  public final String targetNode;

  public LNNewChannelOpenedEvent(String targetNode) {
    this.targetNode = targetNode;
  }
}
