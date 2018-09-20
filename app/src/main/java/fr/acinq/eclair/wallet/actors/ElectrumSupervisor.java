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

package fr.acinq.eclair.wallet.actors;

import org.greenrobot.eventbus.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.util.Date;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import fr.acinq.bitcoin.Protocol;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.Transaction;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.blockchain.electrum.ElectrumClient;
import fr.acinq.eclair.blockchain.electrum.ElectrumWallet;
import fr.acinq.eclair.wallet.DBHelper;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDirection;
import fr.acinq.eclair.wallet.models.PaymentType;
import fr.acinq.eclair.wallet.utils.Constants;

/**
 * This actor handles the various messages received from Electrum Wallet.
 */
public class ElectrumSupervisor extends UntypedActor {

  private final Logger log = LoggerFactory.getLogger(ElectrumSupervisor.class);
  private DBHelper dbHelper;
  private ActorRef paymentRefreshScheduler;
  private ActorRef balanceRefreshScheduler;

  public ElectrumSupervisor(DBHelper dbHelper, final ActorRef paymentRefreshScheduler, final ActorRef balanceRefreshScheduler) {
    this.dbHelper = dbHelper;
    this.paymentRefreshScheduler = paymentRefreshScheduler;
    this.balanceRefreshScheduler = balanceRefreshScheduler;
    context().system().eventStream().subscribe(self(), ElectrumClient.ElectrumEvent.class);
    context().system().eventStream().subscribe(self(), ElectrumWallet.WalletEvent.class);
  }

  /**
   * Handles messages from the wallet: new txs, balance update, tx confidences update.
   *
   * @param message message sent by the wallet
   */
  public void onReceive(final Object message) {

    if (message instanceof ElectrumWallet.TransactionReceived) {
      log.info("received TransactionReceived {}", message);
      ElectrumWallet.TransactionReceived walletTransactionReceive = (ElectrumWallet.TransactionReceived) message;
      final Transaction tx = walletTransactionReceive.tx();
      final PaymentDirection direction = (walletTransactionReceive.received().$greater$eq(walletTransactionReceive.sent()))
        ? PaymentDirection.RECEIVED
        : PaymentDirection.SENT;
      final Satoshi amount = (walletTransactionReceive.received().$greater$eq(walletTransactionReceive.sent()))
        ? walletTransactionReceive.received().$minus(walletTransactionReceive.sent())
        : walletTransactionReceive.sent().$minus(walletTransactionReceive.received());
      final Payment paymentInDB = dbHelper.getPayment(tx.txid().toString(), PaymentType.BTC_ONCHAIN, direction);
      final Satoshi fee = walletTransactionReceive.feeOpt().isDefined() ? walletTransactionReceive.feeOpt().get() : new Satoshi(0);
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      Transaction.write(tx, bos, Protocol.PROTOCOL_VERSION());

      // insert or update received payment in DB
      final Payment paymentReceived = paymentInDB == null ? new Payment() : paymentInDB;
      paymentReceived.setType(PaymentType.BTC_ONCHAIN);
      paymentReceived.setDirection(direction);
      paymentReceived.setReference(walletTransactionReceive.tx().txid().toString());
      if (direction == PaymentDirection.SENT) { // fee makes sense only if the tx is sent by us
        paymentReceived.setFeesPaidMsat(package$.MODULE$.satoshi2millisatoshi(fee).amount());
      }
      paymentReceived.setTxPayload(Hex.toHexString(bos.toByteArray()));
      paymentReceived.setAmountPaidMsat(package$.MODULE$.satoshi2millisatoshi(amount).amount());
      paymentReceived.setConfidenceBlocks((int) walletTransactionReceive.depth());
      paymentReceived.setConfidenceType(0);

      if (paymentInDB == null) {
        // timestamp is updated only if the transaction is not already known
        paymentReceived.setUpdated(new Date());
      }

      dbHelper.insertOrUpdatePayment(paymentReceived);
      paymentRefreshScheduler.tell(Constants.REFRESH, null);
      balanceRefreshScheduler.tell(Constants.REFRESH, null);

    } else if (message instanceof ElectrumWallet.TransactionConfidenceChanged) {
      log.info("received TransactionConfidenceChanged {}", message);
      final ElectrumWallet.TransactionConfidenceChanged tx = (ElectrumWallet.TransactionConfidenceChanged) message;
      final int depth = (int) tx.depth();
      final Payment p = dbHelper.getPayment(tx.txid().toString(), PaymentType.BTC_ONCHAIN);
      if (p != null) {
        p.setConfidenceBlocks(depth);
        dbHelper.updatePayment(p);
      }
      if (depth <= 6) { // don't update the ui for updates in tx with confidence > 6
        paymentRefreshScheduler.tell(Constants.REFRESH, null);
      }

    } else if (message instanceof ElectrumWallet.WalletReady) {
      final ElectrumWallet.WalletReady ready = (ElectrumWallet.WalletReady) message;
      log.info("received WalletReady {}", ready);
      EventBus.getDefault().post(ready);

    } else if (message instanceof ElectrumWallet.NewWalletReceiveAddress) {
      log.info("received NewWalletReceiveAddress message {}", message);
      EventBus.getDefault().postSticky(message);

    } else if (message instanceof ElectrumClient.ElectrumDisconnected$) {
      log.info("received ElectrumDisconnected");
      EventBus.getDefault().post(message);

    } else if (message instanceof ElectrumClient.ElectrumReady) {
      log.info("received ElectrumReady with server {}", ((ElectrumClient.ElectrumReady) message).serverAddress());
      EventBus.getDefault().post(message);

    } else unhandled(message);
  }
}
