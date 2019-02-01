package fr.acinq.eclair.router.helpers;

import fr.acinq.bitcoin.BinaryData;
import fr.acinq.eclair.channel.HasCommitments;
import fr.acinq.eclair.db.ChannelsDb;
import scala.Tuple2;
import scala.collection.Seq;

public class MockChannelsDb implements ChannelsDb {

  public void addOrUpdateChannel(HasCommitments state) {  }

  public void removeChannel(BinaryData channelId) {  }

  public Seq<HasCommitments> listChannels() { return Common.emptyScalaSeq(); }

  public void addOrUpdateHtlcInfo(BinaryData channelId, long commitmentNumber, BinaryData paymentHash, long cltvExpiry) {  }

  public Seq<Tuple2<BinaryData, Object>> listHtlcInfos(BinaryData channelId, long commitmentNumber) { return Common.emptyScalaSeq(); }

  public void close() {  }
}
