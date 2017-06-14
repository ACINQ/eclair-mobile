package fr.acinq.eclair.swordfish.model;

public class ChannelItem {
  public final String id;
  public String status;
  public final long capacitySat;
  public long balanceSat;
  public final String targetPubkey;

  public ChannelItem(String id, long capacitySat, String targetPubkey) {
    this.id = id;
    this.capacitySat = capacitySat;
    this.targetPubkey = targetPubkey;
  }
}
