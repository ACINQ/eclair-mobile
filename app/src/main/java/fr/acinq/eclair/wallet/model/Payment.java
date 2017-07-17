package fr.acinq.eclair.wallet.model;

import com.orm.SugarRecord;
import com.orm.dsl.Unique;

import java.util.Date;
import java.util.List;

public class Payment extends SugarRecord {

  public String type;
  @Unique
  public String paymentReference;
  public String paymentRequest;
  public String description;
  public int confidenceBlocks;
  public int confidenceType;
  public String status;
  public Date created;
  public Date updated;
  public String lastErrorCause;

  public long amountRequestedMsat = 0;
  public long amountPaidMsat = 0;
  public long feesPaidMsat = 0;

  public Payment() {
  }

  public Payment(PaymentTypes type) {
    this.type = type.toString();
  }

  public static Payment getPayment(String reference, PaymentTypes type) {
    if (reference == null || reference.length() == 0) return null;
    List<Payment> payments = Payment.findWithQuery(Payment.class, "SELECT * FROM Payment WHERE payment_reference = ? AND type = ? LIMIT 1",
      reference, type.toString());
    if (payments.isEmpty()) {
      return null;
    } else {
      return payments.get(0);
    }
  }

  public static Payment getPayment(String reference) {
    if (reference == null || reference.length() == 0) return null;
    List<Payment> payments = Payment.findWithQuery(Payment.class, "SELECT * FROM Payment WHERE payment_reference = ? LIMIT 1",
      reference);
    if (payments.isEmpty()) {
      return null;
    } else {
      return payments.get(0);
    }
  }

}
