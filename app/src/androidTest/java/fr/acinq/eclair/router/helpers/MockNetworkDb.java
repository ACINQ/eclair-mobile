package fr.acinq.eclair.router.helpers;

import java.util.ArrayList;

import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.eclair.ShortChannelId;
import fr.acinq.eclair.db.NetworkDb;
import fr.acinq.eclair.wire.ChannelAnnouncement;
import fr.acinq.eclair.wire.ChannelUpdate;
import fr.acinq.eclair.wire.NodeAnnouncement;
import scala.Tuple2;
import scala.collection.Seq;
import scala.collection.immutable.Map;

public class MockNetworkDb implements NetworkDb {

  private java.util.List<String> stringList;

  public MockNetworkDb() {
    stringList = new ArrayList<>();
  }


  public void addNode(NodeAnnouncement n) {  }

  public void updateNode(NodeAnnouncement n) {  }

  public void removeNode(Crypto.PublicKey nodeId) {  }

  // TODO
  public Seq<NodeAnnouncement> listNodes() { return Common.emptyScalaSeq();  }

  public void addChannel(ChannelAnnouncement c, BinaryData txid, Satoshi capacity) {  }

  public void removeChannel(ShortChannelId shortChannelId) {  }

  // TODO
  public Map<ChannelAnnouncement, Tuple2<BinaryData, Satoshi>> listChannels() { return null; }

  public void addChannelUpdate(ChannelUpdate u) {  }

  public void updateChannelUpdate(ChannelUpdate u) {  }

  // TODO
  public Seq<ChannelUpdate> listChannelUpdates() { return Common.emptyScalaSeq();  }

  public void addToPruned(ShortChannelId shortChannelId) {  }

  public void removeFromPruned(ShortChannelId shortChannelId) {  }

  public boolean isPruned(ShortChannelId shortChannelId) { return false; }

  public void close() {  }
}
