package fr.acinq.eclair.swordfish.model;

import com.orm.SugarRecord;

import java.util.Date;

/**
 * Created by Dominique on 18/05/2017.
 */

public class Payment extends SugarRecord {
  public String paymentRequest;
  public String description;
  public int status;
  public Date created;
  public Date updated;

  public Payment(String paymentRequest, String description, Date created, Date updated) {
    this.paymentRequest = paymentRequest;
    this.description = description;
    this.status = 0;
    this.created = created;
    this.updated = updated;
  }
}
