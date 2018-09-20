/*
 * Copyright 2018 ACINQ SAS
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
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;

import java.text.DateFormat;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.adapters.PaymentItemHolder;
import fr.acinq.eclair.wallet.databinding.ActivityLnPaymentDetailsBinding;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDirection;
import fr.acinq.eclair.wallet.models.PaymentStatus;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class LNPaymentDetailsActivity extends EclairActivity {

  private ActivityLnPaymentDetailsBinding mBinding;
  private static final String TAG = "LNPaymentDetails";

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
      if (p.getAmountRequestedMsat() == 0) {
        // this is a donation
        mBinding.amountRequested.setValue(getString(R.string.paymentdetails_amount_requested_donation));
      } else {
        mBinding.amountRequested.setValue(CoinUtils.formatAmountInUnit(new MilliSatoshi(p.getAmountRequestedMsat()), prefUnit, true));
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
