package fr.acinq.eclair.swordfish.events;

import fr.acinq.eclair.swordfish.model.Payment;

public class SWPaymentEvent {
  public final Payment payment;

  public SWPaymentEvent(Payment payment) {
    this.payment = payment;
  }
}
