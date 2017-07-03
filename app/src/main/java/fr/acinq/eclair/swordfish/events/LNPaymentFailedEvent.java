package fr.acinq.eclair.swordfish.events;

import fr.acinq.eclair.swordfish.model.Payment;

public class LNPaymentFailedEvent {
  public final Payment payment;
  public final String cause;

  public LNPaymentFailedEvent(Payment payment, String cause) {
    this.payment = payment;
    this.cause = cause;
  }
}
