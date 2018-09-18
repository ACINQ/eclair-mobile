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
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.CoinUnit;
import fr.acinq.eclair.CoinUtils;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.adapters.PaymentItemHolder;
import fr.acinq.eclair.wallet.databinding.ActivityBitcoinTransactionDetailsBinding;
import fr.acinq.eclair.wallet.models.Payment;
import fr.acinq.eclair.wallet.models.PaymentDirection;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class BitcoinTransactionDetailsActivity extends EclairActivity {

  private final Logger log = LoggerFactory.getLogger(BitcoinTransactionDetailsActivity.class);
  private AlertDialog mRebroadcastDialog;
  private ActivityBitcoinTransactionDetailsBinding mBinding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_bitcoin_transaction_details);

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
      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
      final CoinUnit prefUnit = WalletUtils.getPreferredCoinUnit(prefs);

      final Payment p = app.getDBHelper().getPayment(paymentId);

      final AlertDialog.Builder builder = getCustomDialog(R.string.transactiondetails_rebroadcast_dialog);
      builder.setPositiveButton(R.string.btn_ok, (dialog, id) -> {
        try {
          app.broadcastTx(p.getTxPayload());
          Toast.makeText(getApplicationContext(), getString(R.string.transactiondetails_rebroadcast_success), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
          log.warn("could not broadcast tx {} with cause {}", p.getReference(), e.getMessage());
          Toast.makeText(getApplicationContext(), getString(R.string.transactiondetails_rebroadcast_failure), Toast.LENGTH_LONG).show();
        }
        mRebroadcastDialog.dismiss();
      });
      builder.setNegativeButton(R.string.btn_cancel, (dialog, id) -> mRebroadcastDialog.dismiss());
      mRebroadcastDialog = builder.create();

      mBinding.setIsTxReceived(PaymentDirection.RECEIVED.equals(p.getDirection()));
      mBinding.txAmount.setAmountMsat(new MilliSatoshi(p.getAmountPaidMsat()));
      mBinding.fees.setText(CoinUtils.formatAmountInUnit(new MilliSatoshi(p.getFeesPaidMsat()), prefUnit, true));
      mBinding.txId.setValue(p.getReference());
      mBinding.txId.actionButton.setOnClickListener(WalletUtils.getOpenTxListener(p.getReference()));
      mBinding.date.setValue(DateFormat.getDateTimeInstance().format(p.getUpdated()));
      mBinding.confs.setText(Integer.toString(p.getConfidenceBlocks()));
      mBinding.confs.setTextColor(p.getConfidenceBlocks() >= 6 ? ContextCompat.getColor(getApplicationContext(), R.color.green) : ContextCompat.getColor(getApplicationContext(), R.color.grey_4));
      mBinding.confsType.setValue(Integer.toString(p.getConfidenceType()));
      if (p.getConfidenceBlocks() == 0) {
        mBinding.rebroadcast.setVisibility(View.VISIBLE);
        mBinding.rebroadcast.setOnClickListener(v -> mRebroadcastDialog.show());
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
