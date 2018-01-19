package fr.acinq.eclair.wallet.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.NumberFormat;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.adapters.PaymentItemHolder;
import fr.acinq.eclair.wallet.customviews.DataRow;
import fr.acinq.eclair.wallet.models.Payment;
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

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);

    mAmountPaidRow = findViewById(R.id.transactiondetails_amount);
    mFeesRow = findViewById(R.id.transactiondetails_fees);
    mPaymentHashRow = findViewById(R.id.transactiondetails_txid);
    mUpdateDateRow = findViewById(R.id.transactiondetails_date);
    mTxConfs = findViewById(R.id.transactiondetails_confs);
    mTxConfsType = findViewById(R.id.transactiondetails_confs_type);
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
      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
      final String prefUnit = CoinUtils.getBtcPreferredUnit(prefs);

      final Payment p = app.getDBHelper().getPayment(paymentId);

      final AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setMessage(getResources().getString(R.string.transactiondetails_rebroadcast_dialog));
      builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int id) {
          try {
            app.broadcastTx(p.getTxPayload());
            Toast.makeText(getApplicationContext(), "Sent Broadcast", Toast.LENGTH_LONG).show();
          } catch (Exception e) {
            Log.e(TAG, "Could not broadcast tx:" + p.getReference(), e);
            Toast.makeText(getApplicationContext(), "Broadcast has failed", Toast.LENGTH_LONG).show();
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

      mAmountPaidRow.setValue(CoinUtils.formatAmountInUnitWithUnit(new MilliSatoshi(p.getAmountPaidMsat()), prefUnit));
      mFeesRow.setValue(CoinUtils.formatAmountInUnitWithUnit(new MilliSatoshi(p.getFeesPaidMsat()), prefUnit));
      mPaymentHashRow.setValue(p.getReference());
      mUpdateDateRow.setValue(DateFormat.getDateTimeInstance().format(p.getUpdated()));
      mOpenInExplorer.setOnClickListener(WalletUtils.getOpenTxListener(p.getReference()));
      final int txConfs = p.getConfidenceBlocks();
      mTxConfs.setValue(txConfs > 6 ? "6+" : Integer.toString(txConfs));
      // TODO confidence type should be human readable
      mTxConfsType.setValue(Integer.toString(p.getConfidenceType()));

      if (p.getConfidenceBlocks() == 0) {
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

  @Override
  protected void onResume() {
    super.onResume();
    checkInit();
  }
}
