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

public class LNPaymentDetailsActivity extends EclairActivity {

  private static final String TAG = "LNPaymentDetailsActivity";

  private String paidFormat = "mbtc";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_ln_payment_details);

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
      final DataRow amountPaidRow = (DataRow) findViewById(R.id.paymentdetails_amount_paid);
      amountPaidRow.setValue(CoinUtils.formatAmountMilliBtc(new MilliSatoshi(p.amountPaidMsat)));

      DataRow feesRow = (DataRow) findViewById(R.id.paymentdetails_fees);
      feesRow.setValue(Long.toString(p.feesPaidMsat));

      DataRow statusRow = (DataRow) findViewById(R.id.paymentdetails_status);
      statusRow.setValue(p.status);

      DataRow descRow = (DataRow) findViewById(R.id.paymentdetails_desc);
      descRow.setValue(p.description);

      DataRow amountRequestedRow = (DataRow) findViewById(R.id.paymentdetails_amount_requested);
      amountRequestedRow.setValue(CoinUtils.formatAmountMilliBtc(new MilliSatoshi(p.amountRequestedMsat)));

      DataRow paymentHashRow = (DataRow) findViewById(R.id.paymentdetails_paymenthash);
      paymentHashRow.setValue(p.paymentReference);

      DataRow paymentRequestRow = (DataRow) findViewById(R.id.paymentdetails_paymentrequest);
      paymentRequestRow.setValue(p.paymentRequest);

      DataRow creationDateRow = (DataRow) findViewById(R.id.paymentdetails_created);
      creationDateRow.setValue(DateFormat.getDateTimeInstance().format(p.created));

      DataRow updateDateRow = (DataRow) findViewById(R.id.paymentdetails_updated);
      updateDateRow.setValue(DateFormat.getDateTimeInstance().format(p.updated));

    } catch (Exception e) {
      finish();
    }
  }

}
