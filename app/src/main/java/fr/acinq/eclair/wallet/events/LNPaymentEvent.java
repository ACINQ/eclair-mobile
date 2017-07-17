package fr.acinq.eclair.wallet.events;

import fr.acinq.eclair.wallet.model.Payment;

public class LNPaymentEvent {
  public final Payment payment;

  public LNPaymentEvent(Payment payment) {
    this.payment = payment;
  }
}
