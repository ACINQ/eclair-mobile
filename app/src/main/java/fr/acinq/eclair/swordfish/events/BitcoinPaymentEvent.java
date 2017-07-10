package fr.acinq.eclair.swordfish.events;

import fr.acinq.eclair.swordfish.model.Payment;

public class BitcoinPaymentEvent {
  public final Payment payment;

  public BitcoinPaymentEvent(Payment payment) {
    this.payment = payment;
  }
}
