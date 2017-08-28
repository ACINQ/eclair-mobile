package fr.acinq.eclair.wallet.events;

import fr.acinq.eclair.wallet.models.Payment;

public class BitcoinPaymentEvent {
  public final Payment payment;

  public BitcoinPaymentEvent(Payment payment) {
    this.payment = payment;
  }
}
