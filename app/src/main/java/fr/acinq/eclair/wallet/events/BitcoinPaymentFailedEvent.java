package fr.acinq.eclair.wallet.events;

public class BitcoinPaymentFailedEvent {
  private final String message;

  public BitcoinPaymentFailedEvent(String message) {
    this.message = message;
  }

  public String getMessage() {
    if (message.length() > 20) {
      return message.substring(17) + "...";
    }
    return message;
  }
}
