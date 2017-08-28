package fr.acinq.eclair.wallet;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.listeners.TransactionConfidenceEventListener;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;
import org.greenrobot.eventbus.EventBus;
import org.spongycastle.util.encoders.Hex;

import java.io.File;

import akka.dispatch.Futures;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.blockchain.spv.BitcoinjKit2;
import fr.acinq.eclair.wallet.events.BitcoinPaymentEvent;
import fr.acinq.eclair.wallet.events.WalletBalanceUpdateEvent;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDirection;
import fr.acinq.eclair.wallet.models.PaymentType;
import scala.concurrent.Future;
import scala.concurrent.Promise;

/**
 * Created by PM on 18/07/2017.
 */

public class EclairBitcoinjKit extends BitcoinjKit2 {

  private final Promise<Wallet> pWallet = Futures.promise();
  private final Promise<PeerGroup> pPeerGroup = Futures.promise();
  private final App app;

  public EclairBitcoinjKit(String chain, File datadir, App app) {
    super(chain, datadir);
    this.app = app;
  }

  @Override
  public void onSetupCompleted() {
    pWallet.success(wallet());
    pPeerGroup.success(peerGroup());
    wallet().addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
      @Override
      public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
        final Coin amountReceived = newBalance.minus(prevBalance);
        final Payment paymentInDB = app.getDBHelper().getPayment(tx.getHashAsString(), PaymentType.BTC_ONCHAIN, PaymentDirection.RECEIVED);

        // insert or update received payment in DB
        final Payment paymentReceived = paymentInDB == null ? new Payment() : paymentInDB;
        paymentReceived.setType(PaymentType.BTC_ONCHAIN);
        paymentReceived.setDirection(PaymentDirection.RECEIVED);
        paymentReceived.setReference(tx.getHashAsString());
        paymentReceived.setTxPayload(Hex.toHexString(tx.bitcoinSerialize()));
        paymentReceived.setAmountPaidMsat(package$.MODULE$.satoshi2millisatoshi(
          new Satoshi(amountReceived.getValue())).amount());
        paymentReceived.setUpdated(tx.getUpdateTime());
        paymentReceived.setConfidenceBlocks(tx.getConfidence().getDepthInBlocks());
        paymentReceived.setConfidenceType(tx.getConfidence().getConfidenceType().getValue());
        app.getDBHelper().insertOrUpdatePayment(paymentReceived);

        // dispatch news
        EventBus.getDefault().postSticky(new WalletBalanceUpdateEvent(new Satoshi(newBalance.getValue())));
        EventBus.getDefault().post(new BitcoinPaymentEvent(paymentReceived));
      }
    });
    wallet().addCoinsSentEventListener(new WalletCoinsSentEventListener() {
      @Override
      public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
        final Coin amountSent = newBalance.minus(prevBalance);
        final Payment paymentInDB = app.getDBHelper().getPayment(tx.getHashAsString(), PaymentType.BTC_ONCHAIN, PaymentDirection.SENT);

        // insert or update sent payment in DB
        final Payment paymentSent = paymentInDB == null ? new Payment() : paymentInDB;
        paymentSent.setReference(tx.getHashAsString());
        paymentSent.setType(PaymentType.BTC_ONCHAIN);
        paymentSent.setDirection(PaymentDirection.SENT);
        paymentSent.setTxPayload(Hex.toHexString(tx.bitcoinSerialize()));
        paymentSent.setAmountPaidMsat(package$.MODULE$.satoshi2millisatoshi(
          new Satoshi(amountSent.getValue())).amount());
        if (tx.getFee() != null) {
          paymentSent.setFeesPaidMsat(package$.MODULE$.satoshi2millisatoshi(new Satoshi(tx.getFee().getValue())).amount());
        }
        paymentSent.setUpdated(tx.getUpdateTime());
        paymentSent.setConfidenceBlocks(tx.getConfidence().getDepthInBlocks());
        paymentSent.setConfidenceType(tx.getConfidence().getConfidenceType().getValue());
        app.getDBHelper().insertOrUpdatePayment(paymentSent);

        // dispatch news
        EventBus.getDefault().postSticky(new WalletBalanceUpdateEvent(new Satoshi(newBalance.getValue())));
        EventBus.getDefault().post(new BitcoinPaymentEvent(paymentSent));
      }
    });
    wallet().addTransactionConfidenceEventListener(new TransactionConfidenceEventListener() {
      @Override
      public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
        final Payment p = app.getDBHelper().getPayment(tx.getHashAsString(), PaymentType.BTC_ONCHAIN);
        if (p != null) {
          p.setConfidenceBlocks(tx.getConfidence().getDepthInBlocks());
          p.setConfidenceType(tx.getConfidence().getConfidenceType().getValue());
          app.getDBHelper().updatePayment(p);
        }
      }
    });
    super.onSetupCompleted();
  }

  public Future<Wallet> getFutureWallet() {
    return pWallet.future();
  }

  public Future<PeerGroup> getFuturePeerGroup() {
    return pPeerGroup.future();
  }
}
