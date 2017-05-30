package fr.acinq.eclair.swordfish.model;

public class ChannelItem {
  public final String id;
  public String status;
  public final Long capacity;
  public Long balance;
  public final String targetPubkey;

  public ChannelItem(String id, Long capacity, String targetPubkey) {
    this.id = id;
    this.status = "UNKNOWN";
    this.capacity = capacity;
    this.balance = 0L;
    this.targetPubkey = targetPubkey;
  }
}
