/*
 * Copyright 2019 ACINQ SAS
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

package fr.acinq.eclair.wallet.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import androidx.databinding.DataBindingUtil;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.view.View;
import com.google.android.gms.common.util.Strings;
import fr.acinq.eclair.MilliSatoshi;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.adapters.PaymentItemHolder;
import fr.acinq.eclair.wallet.databinding.ActivityLnPaymentDetailsBinding;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDirection;
import fr.acinq.eclair.wallet.models.PaymentStatus;
import fr.acinq.eclair.wallet.utils.WalletUtils;

import java.text.DateFormat;

public class LNPaymentDetailsActivity extends EclairActivity {

  private ActivityLnPaymentDetailsBinding mBinding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
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
      final boolean isPaymentReceived = PaymentDirection.RECEIVED.equals(p.getDirection());
      mBinding.setIsReceived(isPaymentReceived);

      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
      final CoinUnit prefUnit = WalletUtils.getPreferredCoinUnit(prefs);

      mBinding.amountPaid.setAmountMsat(new MilliSatoshi(p.getAmountPaidMsat()));
      mBinding.amountPaidFiat.setText(getString(R.string.paymentdetails_amount_fiat, WalletUtils.formatMsatToFiatWithUnit(p.getAmountPaidMsat(), WalletUtils.getPreferredFiat(prefs))));

      mBinding.fees.setText(CoinUtils.formatAmountInUnit(new MilliSatoshi(p.getFeesPaidMsat()), prefUnit, true));
      mBinding.status.setText(p.getStatus().toString());
      if (PaymentStatus.PAID == p.getStatus()) {
        mBinding.status.setTextColor(ContextCompat.getColor(this, R.color.green));
      } else if (PaymentStatus.FAILED == p.getStatus()) {
        mBinding.status.setTextColor(ContextCompat.getColor(this, R.color.red_faded));
      } else {
        mBinding.status.setTextColor(ContextCompat.getColor(this, R.color.orange));
      }
      mBinding.recipient.setValue(p.getRecipient());
      if (Strings.isEmptyOrWhitespace(p.getDescription())) {
        mBinding.desc.setText(getString(R.string.receivepayment_lightning_description_notset));
        mBinding.desc.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.grey_2));
        mBinding.desc.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
      } else {
        mBinding.desc.setText(p.getDescription());
      }
      if (p.getAmountRequestedMsat() == 0) {
        // this is a donation
        mBinding.amountRequested.setValue(getString(R.string.paymentdetails_amount_requested_donation));
      } else {
        mBinding.amountRequested.setValue(CoinUtils.formatAmountInUnit(new MilliSatoshi(p.getAmountRequestedMsat()), prefUnit, true));
      }

      if (p.getStatus() == PaymentStatus.FAILED && p.getDirection() == PaymentDirection.SENT) {
        mBinding.retryPayment.setVisibility(View.VISIBLE);
        mBinding.retryPayment.setOnClickListener(v -> {
          final Intent paymentIntent = new Intent(this, SendPaymentActivity.class);
          paymentIntent.putExtra(SendPaymentActivity.EXTRA_INVOICE, p.getPaymentRequest());
          startActivity(paymentIntent);
        });
      } else {
        mBinding.retryPayment.setVisibility(View.GONE);
      }

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
