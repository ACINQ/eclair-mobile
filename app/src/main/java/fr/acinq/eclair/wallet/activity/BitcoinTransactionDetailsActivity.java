package fr.acinq.eclair.wallet.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.bitcoinj.core.TransactionConfidence;

import java.text.DateFormat;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.adapters.PaymentItemHolder;
import fr.acinq.eclair.wallet.customviews.DataRow;
import fr.acinq.eclair.wallet.model.Payment;
import fr.acinq.eclair.wallet.utils.CoinUtils;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class BitcoinTransactionDetailsActivity extends EclairActivity {

  private static final String TAG = "BtcTransactionDetails";

  private DataRow mAmountPaidRow;
  private DataRow mFeesRow;
  private DataRow mPaymentHashRow;
  private DataRow mUpdateDateRow;
  private DataRow mTxConfs;
  private DataRow mTxConfsType;
  private View mOpenInExplorer;
  private View mRebroadcastTxView;
  private AlertDialog mRebroadcastDialog;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_bitcoin_transaction_details);

    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);

    mAmountPaidRow = (DataRow) findViewById(R.id.transactiondetails_amount);
    mFeesRow = (DataRow) findViewById(R.id.transactiondetails_fees);
    mPaymentHashRow = (DataRow) findViewById(R.id.transactiondetails_txid);
    mUpdateDateRow = (DataRow) findViewById(R.id.transactiondetails_date);
    mTxConfs = (DataRow) findViewById(R.id.transactiondetails_confs);
    mTxConfsType = (DataRow) findViewById(R.id.transactiondetails_confs_type);
    mOpenInExplorer = findViewById(R.id.open_in_explorer);
    mRebroadcastTxView = findViewById(R.id.transactiondetails_rebroadcast);
    mRebroadcastTxView.setVisibility(View.GONE);


  }

  @Override
  protected void onStart() {
    super.onStart();
    Intent intent = getIntent();
    long paymentId = intent.getLongExtra(PaymentItemHolder.EXTRA_PAYMENT_ID, -1);
    try {
      final Payment p = Payment.findById(Payment.class, paymentId);

      final AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setMessage(getResources().getString(R.string.transactiondetails_rebroadcast_dialog));
      builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int id) {
          try {
            app.broadcastTx(p.txPayload);
            Toast.makeText(getApplicationContext(), "Broadcast completed", Toast.LENGTH_LONG).show();
          } catch (Exception e) {
            Log.e(TAG, "Could not broadcast tx:" + p.paymentReference, e);
            Toast.makeText(getApplicationContext(),
              "Could not rebroadcast this transaction", Toast.LENGTH_LONG).show();
          }
          mRebroadcastDialog.dismiss();
        }
      });
      builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int id) {
          mRebroadcastDialog.dismiss();
        }
      });
      mRebroadcastDialog = builder.create();

      mAmountPaidRow.setValue(CoinUtils.formatAmountBtc(new MilliSatoshi(p.amountPaidMsat)));
      mFeesRow.setValue(Long.toString(p.feesPaidMsat));
      mPaymentHashRow.setValue(p.paymentReference);
      mUpdateDateRow.setValue(DateFormat.getDateTimeInstance().format(p.updated));
      mOpenInExplorer.setOnClickListener(WalletUtils.getOpenTxListener(p.paymentReference));
      mTxConfs.setValue(Integer.toString(p.confidenceBlocks));
      for (TransactionConfidence.ConfidenceType t : TransactionConfidence.ConfidenceType.values()) {
        if (t.getValue() == p.confidenceType) {
          mTxConfsType.setValue(t.toString());
          break;
        }
      }

      if (p.confidenceBlocks == 0) {
        mRebroadcastTxView.setVisibility(View.VISIBLE);
        mRebroadcastTxView.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            mRebroadcastDialog.show();
          }
        });
      }
    } catch (Exception e) {
      Toast.makeText(this, "Transaction not found", Toast.LENGTH_SHORT).show();
      finish();
    }
  }

}
