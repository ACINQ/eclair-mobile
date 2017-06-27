package fr.acinq.eclair.swordfish.model;

public class ChannelItem {
  public final String id;
  public final long capacityMsat;
  public final String targetPubkey;
  public String status;
  public long balanceMsat;

  public ChannelItem(String id, long capacityMsat, String targetPubkey) {
    this.id = id;
    this.capacityMsat = capacityMsat;
    this.targetPubkey = targetPubkey;
  }
}
