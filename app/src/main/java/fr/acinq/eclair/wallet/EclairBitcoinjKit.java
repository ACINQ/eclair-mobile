package fr.acinq.eclair.wallet;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.listeners.TransactionConfidenceEventListener;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;
import org.greenrobot.eventbus.EventBus;

import java.io.File;

import akka.dispatch.Futures;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.blockchain.spv.BitcoinjKit2;
import fr.acinq.eclair.wallet.events.BitcoinPaymentEvent;
import fr.acinq.eclair.wallet.events.WalletBalanceUpdateEvent;
import fr.acinq.eclair.wallet.model.Payment;
import fr.acinq.eclair.wallet.model.PaymentTypes;
import scala.concurrent.Future;
import scala.concurrent.Promise;

/**
 * Created by PM on 18/07/2017.
 */

public class EclairBitcoinjKit extends BitcoinjKit2 {

  private final Promise<Wallet> pWallet = Futures.promise();

  public EclairBitcoinjKit(String chain, File datadir) {
    super(chain, datadir);
  }

  @Override
  public void onSetupCompleted() {
    pWallet.success(wallet());
    wallet().addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
      @Override
      public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
        EventBus.getDefault().postSticky(new WalletBalanceUpdateEvent(new Satoshi(newBalance.getValue())));
        final Payment paymentInDB = Payment.getPayment(tx.getHashAsString(), PaymentTypes.BTC_RECEIVED);
        final Payment paymentReceived = paymentInDB == null ? new Payment(PaymentTypes.BTC_RECEIVED) : paymentInDB;
        final Coin amountReceived = newBalance.minus(prevBalance);
        paymentReceived.paymentReference = tx.getHashAsString();
        paymentReceived.amountPaidMsat = package$.MODULE$.satoshi2millisatoshi(new Satoshi(amountReceived.getValue())).amount();
        paymentReceived.updated = tx.getUpdateTime();
        paymentReceived.confidenceBlocks = tx.getConfidence().getDepthInBlocks();
        paymentReceived.confidenceType = tx.getConfidence().getConfidenceType().getValue();
        paymentReceived.save();
        EventBus.getDefault().postSticky(new WalletBalanceUpdateEvent(new Satoshi(newBalance.getValue())));
        EventBus.getDefault().post(new BitcoinPaymentEvent(paymentReceived));
      }
    });
    wallet().addCoinsSentEventListener(new WalletCoinsSentEventListener() {
      @Override
      public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
        EventBus.getDefault().postSticky(new WalletBalanceUpdateEvent(new Satoshi(newBalance.getValue())));
        final Payment paymentInDB = Payment.getPayment(tx.getHashAsString(), PaymentTypes.BTC_SENT);
        final Payment paymentSent = paymentInDB == null ? new Payment(PaymentTypes.BTC_SENT) : paymentInDB;
        final Coin amountSent = newBalance.minus(prevBalance);
        paymentSent.paymentReference = tx.getHashAsString();
        paymentSent.amountPaidMsat = package$.MODULE$.satoshi2millisatoshi(new Satoshi(amountSent.getValue())).amount();
        if (tx.getFee() != null)
          paymentSent.feesPaidMsat = package$.MODULE$.satoshi2millisatoshi(new Satoshi(tx.getFee().getValue())).amount();
        paymentSent.updated = tx.getUpdateTime();
        paymentSent.confidenceBlocks = tx.getConfidence().getDepthInBlocks();
        paymentSent.confidenceType = tx.getConfidence().getConfidenceType().getValue();
        paymentSent.save();
        EventBus.getDefault().postSticky(new WalletBalanceUpdateEvent(new Satoshi(newBalance.getValue())));
        EventBus.getDefault().post(new BitcoinPaymentEvent(paymentSent));
      }
    });
    wallet().addTransactionConfidenceEventListener(new TransactionConfidenceEventListener() {
      @Override
      public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
        final Payment paymentInDB = Payment.getPayment(tx.getHashAsString());
        if (paymentInDB != null) {
          paymentInDB.confidenceBlocks = tx.getConfidence().getDepthInBlocks();
          paymentInDB.confidenceType = tx.getConfidence().getConfidenceType().getValue();
          paymentInDB.save();
        }
      }
    });
    super.onSetupCompleted();
  }

  public Future<Wallet> getFutureWallet() {
    return pWallet.future();
  }
}
