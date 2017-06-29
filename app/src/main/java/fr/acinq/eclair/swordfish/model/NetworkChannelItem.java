package fr.acinq.eclair.swordfish.model;

import fr.acinq.bitcoin.Crypto;

public class NetworkChannelItem {
  public final Long shortChannelId;
  public final Crypto.PublicKey nodeId1;
  public final Crypto.PublicKey nodeId2;

  public NetworkChannelItem(Long shortChannelId, Crypto.PublicKey nodeId1, Crypto.PublicKey nodeId2) {
    this.shortChannelId = shortChannelId;
    this.nodeId1 = nodeId1;
    this.nodeId2 = nodeId2;
  }
}
