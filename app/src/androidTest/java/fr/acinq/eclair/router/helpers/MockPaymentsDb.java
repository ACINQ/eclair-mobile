package fr.acinq.eclair.router.helpers;

import fr.acinq.bitcoin.BinaryData;
import fr.acinq.eclair.db.Payment;
import fr.acinq.eclair.db.PaymentsDb;
import scala.Option;
import scala.collection.Seq;

public class MockPaymentsDb implements PaymentsDb {

  public void addPayment(Payment payment) {  }

  public Option<Payment> findByPaymentHash(BinaryData paymentHash) { return null; }

  public Seq<Payment> listPayments() { return Common.emptyScalaSeq(); }

  public void close() {  }
}
