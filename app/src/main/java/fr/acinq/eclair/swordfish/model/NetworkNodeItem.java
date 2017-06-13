package fr.acinq.eclair.swordfish.model;

import fr.acinq.bitcoin.BinaryData;

public class NetworkNodeItem {
  public final BinaryData nodeId;
  public final String alias;

  public NetworkNodeItem(BinaryData nodeId, String alias) {
    this.nodeId = nodeId;
    this.alias = alias;
  }
}
