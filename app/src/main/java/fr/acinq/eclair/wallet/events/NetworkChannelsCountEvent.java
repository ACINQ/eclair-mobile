package fr.acinq.eclair.wallet.events;

public class NetworkChannelsCountEvent {
  public final int count;
  public NetworkChannelsCountEvent(int count) {
    this.count = count;
  }
}
