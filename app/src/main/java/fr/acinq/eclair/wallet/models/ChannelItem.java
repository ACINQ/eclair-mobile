package fr.acinq.eclair.wallet.models;

import fr.acinq.bitcoin.MilliSatoshi;

public class ChannelItem {
  public final String id;
  public final MilliSatoshi capacityMsat;
  public final String targetPubkey;
  public String state;
  public Boolean isCooperativeClosing;
  public MilliSatoshi balanceMsat;

  public ChannelItem(String id, MilliSatoshi capacityMsat, String targetPubkey) {
    this.id = id;
    this.capacityMsat = capacityMsat;
    this.targetPubkey = targetPubkey;
  }
}
