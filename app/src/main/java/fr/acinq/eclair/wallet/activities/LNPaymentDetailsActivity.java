package fr.acinq.eclair.wallet.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;

import java.text.DateFormat;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.adapters.PaymentItemHolder;
import fr.acinq.eclair.wallet.customviews.DataRow;
import fr.acinq.eclair.wallet.databinding.ActivityLnPaymentDetailsBinding;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.wallet.models.PaymentStatus;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class LNPaymentDetailsActivity extends EclairActivity {

  private ActivityLnPaymentDetailsBinding mBinding;
  private static final String TAG = "LNPaymentDetails";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_ln_payment_details);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_ln_payment_details);

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
      final CoinUnit prefUnit = WalletUtils.getPreferredCoinUnit(prefs);

      mBinding.amountPaid.setAmountMsat(new MilliSatoshi(p.getAmountPaidMsat()));
      mBinding.fees.setText(CoinUtils.formatAmountInUnit(new MilliSatoshi(p.getFeesPaidMsat()), prefUnit, true));
      mBinding.status.setText(p.getStatus().name());
      if (PaymentStatus.PAID == p.getStatus()) {
        mBinding.status.setTextColor(ContextCompat.getColor(this, R.color.green));
      } else if (PaymentStatus.FAILED == p.getStatus()) {
        mBinding.status.setTextColor(ContextCompat.getColor(this, R.color.red_faded));
      } else {
        mBinding.status.setTextColor(ContextCompat.getColor(this, R.color.orange));
      }
      mBinding.recipient.setValue(p.getRecipient());
      mBinding.desc.setValue(p.getDescription());
      mBinding.amountRequested.setValue(CoinUtils.formatAmountInUnit(new MilliSatoshi(p.getAmountRequestedMsat()), prefUnit, true));
      mBinding.amountSent.setValue(CoinUtils.formatAmountInUnit(new MilliSatoshi(p.getAmountSentMsat()), prefUnit, true));
      mBinding.paymenthash.setValue(p.getReference());
      mBinding.preimage.setValue(p.getPreimage());
      mBinding.paymentrequest.setValue(p.getPaymentRequest());
      mBinding.created.setValue(DateFormat.getDateTimeInstance().format(p.getCreated()));
      mBinding.updated.setValue(DateFormat.getDateTimeInstance().format(p.getUpdated()));
    } catch (Exception e) {
      finish();
    }
  }
}
