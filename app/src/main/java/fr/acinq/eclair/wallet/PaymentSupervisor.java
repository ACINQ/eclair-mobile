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

package fr.acinq.eclair.wallet;

import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.spongycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.util.Date;

import akka.actor.UntypedActor;
import fr.acinq.bitcoin.Protocol;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.Transaction;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.blockchain.electrum.ElectrumClient;
import fr.acinq.eclair.blockchain.electrum.ElectrumWallet;
import fr.acinq.eclair.wallet.events.PaymentEvent;
import fr.acinq.eclair.wallet.events.ElectrumConnectionEvent;
import fr.acinq.eclair.wallet.events.WalletStateUpdateEvent;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDirection;
import fr.acinq.eclair.wallet.models.PaymentType;

/**
 * This actor handles the various messages received from Electrum Wallet.
 */
public class PaymentSupervisor extends UntypedActor {
  public final static String TAG = "PaymentSupervisor";
  private final static long MAX_DIFF_TIMESTAMP_SEC = 6 * 60 * 60L; // 6 hours
  private DBHelper dbHelper;

  public PaymentSupervisor(DBHelper dbHelper) {
    this.dbHelper = dbHelper;
    context().system().eventStream().subscribe(self(), ElectrumClient.ElectrumEvent.class);
    context().system().eventStream().subscribe(self(), ElectrumWallet.WalletEvent.class);
  }

  /**
   * Handles messages from the wallet: new txs, balance update, tx confidences update.
   *
   * @param message message sent by the wallet
   * @throws Exception
   */
  public void onReceive(final Object message) throws Exception {

    if (message instanceof ElectrumWallet.TransactionReceived) {
      Log.d(TAG, "Received TransactionReceived message: " + message);
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
      Log.d(TAG, "WalletTransactionReceive tx = [ " + walletTransactionReceive.tx().txid()
        + ", amt " + amount + ", fee " + fee + ", dir " + direction
        + ", already known " + (paymentInDB != null) + " ]");

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

      // dispatch news and ask for on-chain balance update
      EventBus.getDefault().post(new PaymentEvent());

    } else if (message instanceof ElectrumWallet.TransactionConfidenceChanged) {
      Log.d(TAG, "Received TransactionConfidenceChanged message: " + message);
      final ElectrumWallet.TransactionConfidenceChanged walletTransactionConfidenceChanged = (ElectrumWallet.TransactionConfidenceChanged) message;
      final int depth = (int) walletTransactionConfidenceChanged.depth();
      final Payment p = dbHelper.getPayment(walletTransactionConfidenceChanged.txid().toString(), PaymentType.BTC_ONCHAIN);
      if (p != null) {
        p.setConfidenceBlocks(depth);
        dbHelper.updatePayment(p);
      }
      if (depth < 10) { // don't update ui for updates in tx with confidence >= 10
        EventBus.getDefault().post(new PaymentEvent());
      }

    } else if (message instanceof ElectrumWallet.WalletReady) {
      final ElectrumWallet.WalletReady ready = (ElectrumWallet.WalletReady) message;
      Long diffTimestamp = Math.abs(System.currentTimeMillis() / 1000L - ready.timestamp());
      Log.d(TAG, "Received WalletReady message with height=" + ready.height() + " and timestamp diff=" + diffTimestamp);
      final Satoshi balance = ready.confirmedBalance().$plus(ready.unconfirmedBalance());
      final boolean isSync = diffTimestamp < MAX_DIFF_TIMESTAMP_SEC;
      EventBus.getDefault().post(new WalletStateUpdateEvent(balance, isSync));
      EventBus.getDefault().postSticky(new ElectrumConnectionEvent(true));

    } else if (message instanceof ElectrumWallet.NewWalletReceiveAddress) {
      Log.d(TAG, "Received NewWalletReceiveAddress message=" + message);
      ElectrumWallet.NewWalletReceiveAddress address = (ElectrumWallet.NewWalletReceiveAddress) message;
      EventBus.getDefault().postSticky(address);

    } else if (message instanceof ElectrumClient.ElectrumDisconnected$) {
      Log.d(TAG, "Received DISCONNECTED");
      EventBus.getDefault().post(new ElectrumConnectionEvent(false));

    } else unhandled(message);
  }
}
