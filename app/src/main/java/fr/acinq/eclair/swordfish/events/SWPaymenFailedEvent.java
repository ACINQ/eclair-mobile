package fr.acinq.eclair.swordfish.events;

import fr.acinq.eclair.payment.PaymentRequest;

public class SWPaymenFailedEvent {
  public final PaymentRequest paymentRequest;
  public final String cause;
  public SWPaymenFailedEvent(PaymentRequest paymentRequest, String cause) {
    this.paymentRequest = paymentRequest;
    this.cause = cause;
  }
}
