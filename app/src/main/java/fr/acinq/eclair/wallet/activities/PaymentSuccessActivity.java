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

import androidx.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AnticipateOvershootInterpolator;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityPaymentSuccessBinding;
import fr.acinq.eclair.wallet.models.PaymentDirection;

public class PaymentSuccessActivity extends EclairActivity {

  public static final String EXTRA_PAYMENTSUCCESS_AMOUNT = BuildConfig.APPLICATION_ID + ".EXTRA_PAYMENTSUCCESS_AMOUNT";
  public static final String EXTRA_PAYMENTSUCCESS_DESC = BuildConfig.APPLICATION_ID + ".EXTRA_PAYMENTSUCCESS_DESC";
  public static final String EXTRA_PAYMENTSUCCESS_DIRECTION = BuildConfig.APPLICATION_ID + ".EXTRA_PAYMENTSUCCESS_DIRECTION";

  private ActivityPaymentSuccessBinding mBinding;
  private Handler dismissHandler;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_payment_success);

    mBinding.paymentsuccessDesc.setText(getIntent().getStringExtra(EXTRA_PAYMENTSUCCESS_DESC));
    mBinding.setIsReceived(PaymentDirection.RECEIVED.toString().equals(getIntent().getStringExtra(EXTRA_PAYMENTSUCCESS_DIRECTION)));

    dismissHandler = new Handler();
    dismissHandler.postDelayed(this::finish, 3500);
    tada();
  }

  private void tada() {
    mBinding.paymentsuccessCircle.setAlpha(0f);
    mBinding.paymentsuccessCircle.setScaleX(0.3f);
    mBinding.paymentsuccessCircle.setScaleY(0.3f);

    mBinding.paymentsuccessCheck.setAlpha(0f);
    mBinding.paymentsuccessCheck.setScaleX(0.6f);
    mBinding.paymentsuccessCheck.setScaleY(0.6f);

    mBinding.paymentsuccessCircle.animate().alpha(1).scaleY(1).scaleX(1).setStartDelay(80).setInterpolator(new AnticipateOvershootInterpolator()).setDuration(350).start();
    mBinding.paymentsuccessCheck.animate().alpha(1).scaleY(1).scaleX(1).setStartDelay(120).setInterpolator(new AnticipateOvershootInterpolator()).setDuration(500).start();
  }

  public void success_tap(View view) {
    tada();
  }

  public void success_dismiss(View view) {
    dismissHandler.removeCallbacks(null);
    finish();
  }
}
