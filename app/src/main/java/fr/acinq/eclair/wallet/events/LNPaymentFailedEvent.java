package fr.acinq.eclair.wallet.events;

import fr.acinq.eclair.wallet.models.Payment;

public class LNPaymentFailedEvent {
  public final Payment payment;
  public final String cause;
  public final String detailedCause;

  public LNPaymentFailedEvent(Payment payment, String cause, String detailedCause) {
    this.payment = payment;
    this.cause = cause;
    this.detailedCause = detailedCause;
  }
}
