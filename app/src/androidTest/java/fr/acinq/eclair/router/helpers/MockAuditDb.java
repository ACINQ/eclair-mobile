package fr.acinq.eclair.router.helpers;

import fr.acinq.eclair.channel.NetworkFeePaid;
import fr.acinq.eclair.db.AuditDb;
import fr.acinq.eclair.db.NetworkFee;
import fr.acinq.eclair.db.Stats;
import fr.acinq.eclair.payment.PaymentReceived;
import fr.acinq.eclair.payment.PaymentRelayed;
import fr.acinq.eclair.payment.PaymentSent;
import scala.collection.Seq;

public class MockAuditDb implements AuditDb {

  public void add(PaymentSent paymentSent) {  }

  public void add(PaymentReceived paymentReceived) {  }

  public void add(PaymentRelayed paymentRelayed) {  }

  public void add(NetworkFeePaid networkFeePaid) {  }

  public Seq<PaymentSent> listSent(long from, long to) { return Common.emptyScalaSeq(); }

  public Seq<PaymentReceived> listReceived(long from, long to) { return Common.emptyScalaSeq(); }

  public Seq<PaymentRelayed> listRelayed(long from, long to) { return Common.emptyScalaSeq(); }

  public Seq<NetworkFee> listNetworkFees(long from, long to) {
    return null;
  }

  public Seq<Stats> stats() {
    return Common.emptyScalaSeq();
  }

  public void close() {  }
}
