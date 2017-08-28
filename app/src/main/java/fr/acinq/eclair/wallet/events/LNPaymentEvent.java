package fr.acinq.eclair.wallet.events;

import fr.acinq.eclair.wallet.models.Payment;

public class LNPaymentEvent {
  public final Payment payment;

  public LNPaymentEvent(Payment payment) {
    this.payment = payment;
  }
}
