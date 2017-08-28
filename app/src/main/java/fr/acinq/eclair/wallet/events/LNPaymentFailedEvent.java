package fr.acinq.eclair.wallet.events;

import fr.acinq.eclair.wallet.models.Payment;

public class LNPaymentFailedEvent {
  public final Payment payment;
  public final String cause;

  public LNPaymentFailedEvent(Payment payment, String cause) {
    this.payment = payment;
    this.cause = cause;
  }
}
