package fr.acinq.eclair.wallet.events;

import java.util.ArrayList;
import java.util.List;

import fr.acinq.eclair.wallet.models.LightningPaymentError;

public class LNPaymentFailedEvent {
  public final String paymentHash;
  public final String paymentDescription;
  public final boolean isSimple;
  public final String simpleMessage;
  public final ArrayList<LightningPaymentError> errors;

  public LNPaymentFailedEvent(final String paymentHash, final String paymentDescription, final boolean isSimple, final String simpleMessage, final ArrayList<LightningPaymentError> errors) {
    this.paymentHash = paymentHash;
    this.paymentDescription = paymentDescription;
    this.isSimple = isSimple;
    this.simpleMessage = simpleMessage;
    this.errors = errors;
  }
}
