package fr.acinq.eclair.wallet.events;

import fr.acinq.eclair.wallet.model.Payment;

public class BitcoinPaymentEvent {
  public final Payment payment;

  public BitcoinPaymentEvent(Payment payment) {
    this.payment = payment;
  }
}
