package fr.acinq.eclair.swordfish.model;

import fr.acinq.bitcoin.BinaryData;

public class NetworkChannelItem {
  public final Long shortChannelId;
  public final BinaryData nodeId1;
  public final BinaryData nodeId2;

  public NetworkChannelItem(Long shortChannelId, BinaryData nodeId1, BinaryData nodeId2) {
    this.shortChannelId = shortChannelId;
    this.nodeId1 = nodeId1;
    this.nodeId2 = nodeId2;
  }
}
