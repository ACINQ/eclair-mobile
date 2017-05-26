package fr.acinq.eclair.swordfish.model;

/**
 * Created by Dominique on 19/05/2017.
 */

public class PaymentRequest {
  public final String nodeId;
  public final Long amountMsat;
  public final String paymentHash;

  public PaymentRequest(String nodeId, Long amountMsat, String paymentHash) {
    this.nodeId = nodeId;
    this.amountMsat = amountMsat;
    this.paymentHash = paymentHash;
  }

  /**
   * Returns a Stringified payment request
   *
   * @param pr
   * @return
   */
  public static String write(PaymentRequest pr) {
    return pr == null ? "N/A" : (new StringBuffer())
      .append(pr.nodeId).append(":")
      .append(pr.amountMsat).append(":")
      .append(pr.paymentHash)
      .toString();
  }

  /**
   * Extract a payment request from a string. If the string is not a valid stringified
   * payment request, throws an IllegalArgmentException.
   *
   * @param str
   * @return a OKish PaymentRequest object
   */
  public static PaymentRequest read(String str) {
    String[] array = str.split(":", 3);
    if (array.length == 3) {
      String nodeId = array[0];
      Long amount = Long.parseLong(array[1]);
      String paymentHash = array[2];
      if (!"".equals(nodeId.trim()) && !"".equals(paymentHash.trim()) && amount > 0) {
        return new PaymentRequest(nodeId, amount, paymentHash);
      }
    }
    throw new IllegalArgumentException("");
  }

  @Override
  public String toString() {
    return PaymentRequest.write(this);
  }
}
