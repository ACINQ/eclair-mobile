package fr.acinq.eclair.wallet.events;

public class NetworkNodesCountEvent {
  public final int count;
  public NetworkNodesCountEvent(int count) {
    this.count = count;
  }
}
