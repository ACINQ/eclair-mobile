package fr.acinq.eclair.swordfish.events;

import fr.acinq.eclair.payment.PaymentRequest;

public class SWPaymentEvent {
  public final PaymentRequest paymentRequest;
  public SWPaymentEvent(PaymentRequest paymentRequest) {
    this.paymentRequest = paymentRequest;
  }
}
