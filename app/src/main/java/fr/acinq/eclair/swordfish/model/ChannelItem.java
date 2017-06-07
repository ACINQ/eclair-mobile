package fr.acinq.eclair.swordfish.model;

import fr.acinq.bitcoin.Satoshi;

public class ChannelItem {
  public final String id;
  public String status;
  public final Satoshi capacity;
  public Satoshi balance;
  public final String targetPubkey;

  public ChannelItem(String id, Satoshi capacity, String targetPubkey) {
    this.id = id;
    this.capacity = capacity;
    this.targetPubkey = targetPubkey;
  }
}
