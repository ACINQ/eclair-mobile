package fr.acinq.eclair.wallet;

import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.spongycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.util.Date;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.Protocol;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.Transaction;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.blockchain.electrum.ElectrumClient;
import fr.acinq.eclair.blockchain.electrum.ElectrumWallet;
import fr.acinq.eclair.wallet.events.BitcoinPaymentEvent;
import fr.acinq.eclair.wallet.events.WalletBalanceUpdateEvent;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDirection;
import fr.acinq.eclair.wallet.models.PaymentType;

/**
 * This actor handles the various messages received from Electrum Wallet.
 */
public class PaymentSupervisor extends UntypedActor {
  public final static String TAG = "PaymentSupervisor";
  private App app;
  private ActorRef wallet;

  public PaymentSupervisor(App app, ActorRef wallet) {
    this.app = app;
    this.wallet = wallet;
    wallet.tell(new ElectrumClient.AddStatusListener(getSelf()), getSelf());
  }

  /**
   * Handles messages from the wallet: new txs, balance update, tx confidences update.
   *
   * @param message message sent by the wallet
   * @throws Exception
   */
  public void onReceive(Object message) throws Exception {
    if (message instanceof ElectrumWallet.WalletTransactionReceive) {
      Log.d(TAG, "Received WalletTransactionReceive message: " + message);
      ElectrumWallet.WalletTransactionReceive walletTransactionReceive = (ElectrumWallet.WalletTransactionReceive) message;
      final Transaction tx = walletTransactionReceive.tx();
      final PaymentDirection direction = (walletTransactionReceive.received().$greater$eq(walletTransactionReceive.sent()))
        ? PaymentDirection.RECEIVED
        : PaymentDirection.SENT;
      final Satoshi amount = (walletTransactionReceive.received().$greater$eq(walletTransactionReceive.sent()))
        ? walletTransactionReceive.received().$minus(walletTransactionReceive.sent())
        : walletTransactionReceive.sent().$minus(walletTransactionReceive.received());
      final Payment paymentInDB = app.getDBHelper().getPayment(tx.txid().toString(), PaymentType.BTC_ONCHAIN, direction);
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      Transaction.write(tx, bos, Protocol.PROTOCOL_VERSION());

      // insert or update received payment in DB
      final Payment paymentReceived = paymentInDB == null ? new Payment() : paymentInDB;
      paymentReceived.setType(PaymentType.BTC_ONCHAIN);
      paymentReceived.setDirection(direction);
      paymentReceived.setReference(walletTransactionReceive.tx().txid().toString());
      paymentReceived.setTxPayload(Hex.toHexString(bos.toByteArray()));
      paymentReceived.setAmountPaidMsat(package$.MODULE$.satoshi2millisatoshi(amount).amount());
      paymentReceived.setConfidenceBlocks((int) walletTransactionReceive.depth());
      paymentReceived.setConfidenceType(0);
      if (paymentInDB == null) {
        // timestamp is updated only if the transaction is not already known
        paymentReceived.setUpdated(new Date());
      }
      app.getDBHelper().insertOrUpdatePayment(paymentReceived);

      // dispatch news and ask for on-chain balance update
      app.requestOnchainBalanceUpdate();
      EventBus.getDefault().post(new BitcoinPaymentEvent(paymentReceived));
    } else if (message instanceof ElectrumWallet.WalletTransactionConfidenceChanged) {
      Log.d(TAG, "Received WalletTransactionConfidenceChanged message: " + message);
      final ElectrumWallet.WalletTransactionConfidenceChanged walletTransactionConfidenceChanged = (ElectrumWallet.WalletTransactionConfidenceChanged) message;
      final Payment p = app.getDBHelper().getPayment(walletTransactionConfidenceChanged.txid().toString(), PaymentType.BTC_ONCHAIN);
      if (p != null) {
        p.setConfidenceBlocks((int) walletTransactionConfidenceChanged.depth());
        app.getDBHelper().updatePayment(p);
      }
    } else if (message instanceof ElectrumWallet.GetBalanceResponse) {
      Log.d(TAG, "Received GetBalanceResponse message: " + message);
      final ElectrumWallet.GetBalanceResponse getBalanceResponse = (ElectrumWallet.GetBalanceResponse) message;
      final Satoshi total = getBalanceResponse.confirmed().$plus(getBalanceResponse.unconfirmed());
      // if total amount equals 0, lets get the balance from payment DB
      if (total.amount() == 0) {
        EventBus.getDefault().postSticky(new WalletBalanceUpdateEvent(
          package$.MODULE$.millisatoshi2satoshi(new MilliSatoshi(app.getDBHelper().getOnchainBalanceMsat()))));
      } else {
        EventBus.getDefault().postSticky(new WalletBalanceUpdateEvent(total));
      }
    } else unhandled(message);
  }
}
