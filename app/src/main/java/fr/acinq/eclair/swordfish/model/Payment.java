package fr.acinq.eclair.swordfish.model;

import com.orm.SugarRecord;
import com.orm.dsl.Unique;

import java.util.Date;

/**
 * Created by Dominique on 18/05/2017.
 */

public class Payment extends SugarRecord {
  @Unique
  public String paymentHash;

  public String paymentRequest;
  public String description;
  public String status;
  public Date created;
  public Date updated;

  public String amountPaid;
  public String feesPaid;

  public Payment() {}

  public Payment(String paymentHash, String paymentRequest, String description, Date created, Date updated) {
    this.paymentHash = paymentHash;
    this.paymentRequest = paymentRequest;
    this.description = description;
    this.status = "PENDING";
    this.created = created;
    this.updated = updated;
  }
}
