package fr.acinq.eclair.wallet.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;

import java.text.DateFormat;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.adapters.PaymentItemHolder;
import fr.acinq.eclair.wallet.customviews.DataRow;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.utils.CoinUtils;

public class LNPaymentDetailsActivity extends EclairActivity {

  private static final String TAG = "LNPaymentDetailsActivity";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_ln_payment_details);

    Toolbar toolbar = findViewById(R.id.toolbar);
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
      final Payment p = app.getDBHelper().getPayment(paymentId);

      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
      final String prefUnit = CoinUtils.getBtcPreferredUnit(prefs);

      final DataRow amountPaidRow = findViewById(R.id.paymentdetails_amount_paid);
      amountPaidRow.setValue(CoinUtils.formatAmountInUnitWithUnit(new MilliSatoshi(p.getAmountPaidMsat()), prefUnit));

      DataRow feesRow = findViewById(R.id.paymentdetails_fees);
      feesRow.setValue(CoinUtils.formatAmountInUnitWithUnit(new MilliSatoshi(p.getFeesPaidMsat()), prefUnit));

      DataRow statusRow = findViewById(R.id.paymentdetails_status);
      statusRow.setValue(p.getStatus().name());

      DataRow recipientRow = findViewById(R.id.paymentdetails_recipient);
      recipientRow.setValue(p.getRecipient());

      DataRow descRow = findViewById(R.id.paymentdetails_desc);
      descRow.setValue(p.getDescription());

      DataRow amountRequestedRow = findViewById(R.id.paymentdetails_amount_requested);
      amountRequestedRow.setValue(CoinUtils.formatAmountInUnitWithUnit(new MilliSatoshi(p.getAmountRequestedMsat()), prefUnit));

      DataRow paymentHashRow = findViewById(R.id.paymentdetails_paymenthash);
      paymentHashRow.setValue(p.getReference());

      DataRow preimageRow = findViewById(R.id.paymentdetails_preimage);
      preimageRow.setValue(p.getPreimage());

      DataRow paymentRequestRow = findViewById(R.id.paymentdetails_paymentrequest);
      paymentRequestRow.setValue(p.getPaymentRequest());

      DataRow creationDateRow = findViewById(R.id.paymentdetails_created);
      creationDateRow.setValue(DateFormat.getDateTimeInstance().format(p.getCreated()));

      DataRow updateDateRow = findViewById(R.id.paymentdetails_updated);
      updateDateRow.setValue(DateFormat.getDateTimeInstance().format(p.getUpdated()));

    } catch (Exception e) {
      finish();
    }
  }

}
