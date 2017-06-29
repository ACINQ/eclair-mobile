package fr.acinq.eclair.swordfish.model;

import fr.acinq.bitcoin.Crypto;

public class NetworkNodeItem {
  public final Crypto.PublicKey nodeId;
  public final String alias;

  public NetworkNodeItem(Crypto.PublicKey nodeId, String alias) {
    this.nodeId = nodeId;
    this.alias = alias;
  }
}
