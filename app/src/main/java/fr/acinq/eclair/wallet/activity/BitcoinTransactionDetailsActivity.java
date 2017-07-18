package fr.acinq.eclair.wallet.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.View;

import java.text.DateFormat;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.adapters.PaymentItemHolder;
import fr.acinq.eclair.wallet.customviews.DataRow;
import fr.acinq.eclair.wallet.model.Payment;
import fr.acinq.eclair.wallet.utils.CoinUtils;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class BitcoinTransactionDetailsActivity extends EclairActivity {

  private static final String TAG = "BitcoinTransactionDetailsActivity";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_bitcoin_transaction_details);

    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);
  }

  @Override
  protected void onStart() {
    super.onStart();
    Intent intent = getIntent();
    long paymentId = intent.getLongExtra(PaymentItemHolder.EXTRA_PAYMENT_ID, -1);
    try {
      final Payment p = Payment.findById(Payment.class, paymentId);

      // amount
      DataRow amountPaidRow = (DataRow) findViewById(R.id.transactiondetails_amount);
      amountPaidRow.setValue(CoinUtils.formatAmountBtc(new MilliSatoshi(p.amountPaidMsat)));

      DataRow feesRow = (DataRow) findViewById(R.id.transactiondetails_fees);
      feesRow.setValue(Long.toString(p.feesPaidMsat));

      DataRow paymentHashRow = (DataRow) findViewById(R.id.transactiondetails_txid);
      paymentHashRow.setValue(p.paymentReference);

      DataRow updateDateRow = (DataRow) findViewById(R.id.transactiondetails_date);
      updateDateRow.setValue(DateFormat.getDateTimeInstance().format(p.updated));

      View openInExplorer = findViewById(R.id.open_in_explorer);
      openInExplorer.setOnClickListener(WalletUtils.getOpenTxListener(p.paymentReference));

    } catch (Exception e) {
      finish();
    }
  }

}
