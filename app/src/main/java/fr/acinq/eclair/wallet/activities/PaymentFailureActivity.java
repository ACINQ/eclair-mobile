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
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.animation.AnticipateOvershootInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.adapters.LightningErrorListAdapter;
import fr.acinq.eclair.wallet.models.LightningPaymentError;

public class PaymentFailureActivity extends EclairActivity {

  public static final String EXTRA_PAYMENT_HASH = BuildConfig.APPLICATION_ID + "EXTRA_PAYMENT_HASH";
  public static final String EXTRA_PAYMENT_DESC = BuildConfig.APPLICATION_ID + "EXTRA_PAYMENT_DESC";
  public static final String EXTRA_PAYMENT_SIMPLE_ONLY = BuildConfig.APPLICATION_ID + "EXTRA_SIMPLE_ONLY";
  public static final String EXTRA_PAYMENT_SIMPLE_MESSAGE = BuildConfig.APPLICATION_ID + "EXTRA_SIMPLE_MESSAGE";
  public static final String EXTRA_PAYMENT_ERRORS = BuildConfig.APPLICATION_ID + "EXTRA_PAYMENT_ERRORS";
  private static final String TAG = "PaymentFailureActivity";

  private View mPaymentDescView;
  private TextView mPaymentDescValue;
  private TextView mMessageView;
  private View mDetails;
  private Button mShowDetailed;
  private ImageButton mClose;
  private RecyclerView mErrorsView;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_payment_failure);
    mMessageView = findViewById(R.id.paymentfailure_simple);
    mDetails = findViewById(R.id.payment_failure_details);
    mPaymentDescView = findViewById(R.id.paymentfailure_paymentdesc);
    mPaymentDescValue = findViewById(R.id.paymentfailure_paymentdesc_value);
    mShowDetailed = findViewById(R.id.paymentfailure_show_details);
    mErrorsView = findViewById(R.id.paymentfailure_errors);

    final Intent intent = getIntent();
    final String paymentDescription = intent.getStringExtra(EXTRA_PAYMENT_DESC);
    final String simpleMessage = intent.getStringExtra(EXTRA_PAYMENT_SIMPLE_MESSAGE);
    final boolean displaySimpleMessageOnly = intent.getBooleanExtra(EXTRA_PAYMENT_SIMPLE_ONLY, true);

    mPaymentDescValue.setText(paymentDescription);
    if (displaySimpleMessageOnly) {
      mMessageView.setText(simpleMessage);
      mShowDetailed.setVisibility(View.GONE);
      mPaymentDescView.setVisibility(View.VISIBLE);
    } else {
      final List<LightningPaymentError> errors = intent.getParcelableArrayListExtra(EXTRA_PAYMENT_ERRORS);
      mMessageView.setText(getString(R.string.paymentfailure_error_size, errors.size()));

      mErrorsView.setHasFixedSize(true);
      mErrorsView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
      mErrorsView.setAdapter(new LightningErrorListAdapter(errors));

      mShowDetailed.setOnClickListener(view -> {
        mShowDetailed.setVisibility(View.GONE);
        mPaymentDescView.setVisibility(View.VISIBLE);
        mErrorsView.setVisibility(View.VISIBLE);
      });
    }

    mClose = findViewById(R.id.paymentfailure_close);
    mClose.setOnClickListener(view -> finish());

    // animation
    final ImageView mSadImage = findViewById(R.id.paymentfailure_sad);
    mSadImage.setAlpha(0f);
    mSadImage.setScaleX(0.6f);
    mSadImage.setScaleY(0.6f);
    mSadImage.animate().alpha(1).scaleX(1).scaleY(1)
      .setInterpolator(new AnticipateOvershootInterpolator()).setStartDelay(80).setDuration(500).start();
  }
}
