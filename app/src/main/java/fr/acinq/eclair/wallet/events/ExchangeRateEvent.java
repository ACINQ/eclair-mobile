package fr.acinq.eclair.wallet.events;

public class ExchangeRateEvent {
  public final Double eur_btc;
  public final Double usd_btc;

  public ExchangeRateEvent(Double eur_btc, Double usd_btc) {
    this.eur_btc = eur_btc;
    this.usd_btc = usd_btc;
  }
}
