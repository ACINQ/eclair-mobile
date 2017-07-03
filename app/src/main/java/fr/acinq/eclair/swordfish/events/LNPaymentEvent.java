package fr.acinq.eclair.swordfish.events;

import fr.acinq.eclair.swordfish.model.Payment;

public class LNPaymentEvent {
  public final Payment payment;

  public LNPaymentEvent(Payment payment) {
    this.payment = payment;
  }
}
