package fr.acinq.eclair.wallet.models;

public class FeeRating {
  public final int rating;
  public final String label;

  public FeeRating(int rating, String label) {
    this.rating = rating;
    this.label = label;
  }
}
