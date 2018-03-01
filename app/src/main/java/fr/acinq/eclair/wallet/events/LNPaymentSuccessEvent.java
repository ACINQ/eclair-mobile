package fr.acinq.eclair.wallet.events;

import fr.acinq.eclair.wallet.models.Payment;

public class LNPaymentSuccessEvent {
  public final Payment payment;

  public LNPaymentSuccessEvent(Payment payment) {
    this.payment = payment;
  }
}
