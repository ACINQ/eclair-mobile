package fr.acinq.eclair.swordfish.model;

public enum PaymentTypes {
  LN("LN"),
  BTC_RECEIVED("BTC_RECEIVED"),
  BTC_SENT("BTC_SENT");

  private final String type;

  PaymentTypes(String type) {
    this.type = type;
  }

  @Override
  public String toString() {
    return this.type;
  }
}
