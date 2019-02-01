package fr.acinq.eclair.router.helpers;

import java.net.InetSocketAddress;

import fr.acinq.bitcoin.Crypto;
import fr.acinq.eclair.db.PeersDb;
import scala.collection.immutable.Map;

public class MockPeersDb implements PeersDb {

  public void addOrUpdatePeer(Crypto.PublicKey nodeId, InetSocketAddress address) {  }

  public void removePeer(Crypto.PublicKey nodeId) {  }

  public Map<Crypto.PublicKey, InetSocketAddress> listPeers() {
    return null;//Common.emptyScalaMap();
  }

  public void close() {  }
}
