/*
 * Copyright 2018 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.eclair.wallet.models;

import org.greenrobot.greendao.annotation.Convert;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Index;
import org.greenrobot.greendao.annotation.NotNull;

import java.util.Date;

@Entity(indexes = {
  @Index(value = "type, reference", unique = true)
})
public class Payment {

  @Id
  private Long id;
  /**
   * Type of the payment.
   */
  @NotNull
  @Convert(converter = PaymentTypeConverter.class, columnType = String.class)
  private PaymentType type;
  /**
   * "Direction" of the payment.
   */
  @NotNull
  @Convert(converter = PaymentDirectionConverter.class, columnType = String.class)
  private PaymentDirection direction;
  /**
   * Tx id if the payment is an on-chain transaction.
   * Payment Hash if the payment is a LN payment.
   */
  @NotNull
  private String reference;

  /**
   * Recipient of the payment, node id for LN or address for onchain
   */
  private String recipient;
  /**
   * LN payment preimage, prooving that the payment was made
   */
  private String preimage;
  /**
   * Serialized LN Payment request
   */
  private String paymentRequest;
  private String description;
  /**
   * Tx confirmations count
   */
  private int confidenceBlocks;
  private int confidenceType;
  /**
   * Payload of the transaction. Empty for a LN payment. Helps to rebroadcast a tx.
   */
  private String txPayload;
  /**
   * Status of the payment.
   * {@link PaymentStatus}
   */
  @Convert(converter = PaymentStatusConverter.class, columnType = String.class)
  private PaymentStatus status;

  @NotNull
  private Date created;

  private Date updated;
  /**
   * If the payment has failed, contains the last known error message.
   */
  private String lastErrorCause;
  /**
   * Payment requested (without the fees).
   */
  private long amountRequestedMsat = 0;
  /**
   * Payment effectively sent (can be different from amount Requested).
   */
  private long amountSentMsat = 0;
  /**
   * Payment effectively paid (with the fees).
   */
  private long amountPaidMsat = 0;
  /**
   * Fees amount.
   */
  private long feesPaidMsat = 0;

  public Payment() {
    this.created = new Date();
  }

  public Payment(PaymentType type) {
    this.type = type;
  }

  @Generated(hash = 1357595814)
  public Payment(Long id, @NotNull PaymentType type, @NotNull PaymentDirection direction, @NotNull String reference, String recipient, String preimage,
          String paymentRequest, String description, int confidenceBlocks, int confidenceType, String txPayload, PaymentStatus status, @NotNull Date created, Date updated,
          String lastErrorCause, long amountRequestedMsat, long amountSentMsat, long amountPaidMsat, long feesPaidMsat) {
      this.id = id;
      this.type = type;
      this.direction = direction;
      this.reference = reference;
      this.recipient = recipient;
      this.preimage = preimage;
      this.paymentRequest = paymentRequest;
      this.description = description;
      this.confidenceBlocks = confidenceBlocks;
      this.confidenceType = confidenceType;
      this.txPayload = txPayload;
      this.status = status;
      this.created = created;
      this.updated = updated;
      this.lastErrorCause = lastErrorCause;
      this.amountRequestedMsat = amountRequestedMsat;
      this.amountSentMsat = amountSentMsat;
      this.amountPaidMsat = amountPaidMsat;
      this.feesPaidMsat = feesPaidMsat;
  }

  public Long getId() {
    return this.id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public PaymentType getType() {
    return this.type;
  }

  public void setType(PaymentType type) {
    this.type = type;
  }

  public PaymentDirection getDirection() {
    return this.direction;
  }

  public void setDirection(PaymentDirection direction) {
    this.direction = direction;
  }

  public String getReference() {
    return this.reference;
  }

  public void setReference(String reference) {
    this.reference = reference;
  }

  public String getPaymentRequest() {
    return this.paymentRequest;
  }

  public void setPaymentRequest(String paymentRequest) {
    this.paymentRequest = paymentRequest;
  }

  public String getDescription() {
    return this.description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public int getConfidenceBlocks() {
    return this.confidenceBlocks;
  }

  public void setConfidenceBlocks(int confidenceBlocks) {
    this.confidenceBlocks = confidenceBlocks;
  }

  public int getConfidenceType() {
    return this.confidenceType;
  }

  public void setConfidenceType(int confidenceType) {
    this.confidenceType = confidenceType;
  }

  public String getTxPayload() {
    return this.txPayload;
  }

  public void setTxPayload(String txPayload) {
    this.txPayload = txPayload;
  }

  public Date getCreated() {
    return this.created;
  }

  public void setCreated(Date created) {
    this.created = created;
  }

  public Date getUpdated() {
    return this.updated;
  }

  public void setUpdated(Date updated) {
    this.updated = updated;
  }

  public String getLastErrorCause() {
    return this.lastErrorCause;
  }

  public void setLastErrorCause(String lastErrorCause) {
    this.lastErrorCause = lastErrorCause;
  }

  public long getAmountRequestedMsat() {
    return this.amountRequestedMsat;
  }

  public void setAmountRequestedMsat(long amountRequestedMsat) {
    this.amountRequestedMsat = amountRequestedMsat;
  }

  public long getAmountSentMsat() {
    return this.amountSentMsat;
  }

  public void setAmountSentMsat(long amountSentMsat) {
    this.amountSentMsat = amountSentMsat;
  }

  public long getAmountPaidMsat() {
    return this.amountPaidMsat;
  }

  public void setAmountPaidMsat(long amountPaidMsat) {
    this.amountPaidMsat = amountPaidMsat;
  }

  public long getFeesPaidMsat() {
    return this.feesPaidMsat;
  }

  public void setFeesPaidMsat(long feesPaidMsat) {
    this.feesPaidMsat = feesPaidMsat;
  }

  public String getPreimage() {
    return preimage;
  }

  public void setPreimage(String preimage) {
    this.preimage = preimage;
  }

  public String getRecipient() {
    return recipient;
  }

  public void setRecipient(String recipient) {
    this.recipient = recipient;
  }

  public PaymentStatus getStatus() {
    return this.status;
  }

  public void setStatus(PaymentStatus status) {
    this.status = status;
  }

}
