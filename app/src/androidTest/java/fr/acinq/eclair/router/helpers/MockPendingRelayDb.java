package fr.acinq.eclair.router.helpers;

import fr.acinq.bitcoin.BinaryData;
import fr.acinq.eclair.channel.Command;
import fr.acinq.eclair.db.PendingRelayDb;
import scala.collection.Seq;

public class MockPendingRelayDb implements PendingRelayDb {

  public void addPendingRelay(BinaryData channelId, long htlcId, Command cmd) {  }

  public void removePendingRelay(BinaryData channelId, long htlcId) {  }

  public Seq<Command> listPendingRelay(BinaryData channelId) { return Common.emptyScalaSeq(); }

  public void close() { }
}
