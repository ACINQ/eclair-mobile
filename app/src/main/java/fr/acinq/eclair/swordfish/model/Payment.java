package fr.acinq.eclair.swordfish.model;

import com.orm.SugarRecord;
import com.orm.dsl.Unique;

import java.util.Date;

public class Payment extends SugarRecord {
  @Unique
  public String paymentHash;

  public String paymentRequest;
  public String description;
  public String status;
  public Date created;
  public Date updated;
  public String lastErrorCause;

  public long amountRequested = 0;
  public long amountPaid = 0;
  public long feesPaid = 0;

  public Payment() {}

}
