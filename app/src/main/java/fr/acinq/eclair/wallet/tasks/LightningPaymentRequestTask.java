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

package fr.acinq.eclair.wallet.tasks;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;

import com.google.zxing.qrcode.QRCodeWriter;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.wallet.activities.EclairActivity;
import scala.Option;

public class LightningPaymentRequestTask extends AsyncTask<Object, Integer, PaymentRequest> {

  public interface AsyncPaymentRequestResponse {
    void processLightningPaymentRequest(final PaymentRequest paymentRequest);
  }

  private static final String TAG = LightningPaymentRequestTask.class.getSimpleName();
  private AsyncPaymentRequestResponse delegate;
  private EclairActivity activity;

  public LightningPaymentRequestTask(AsyncPaymentRequestResponse delegate, EclairActivity activity){
    this.delegate = delegate;
    this.activity = activity;
  }

  @Override
  protected PaymentRequest doInBackground(Object... params) {
    if (this.activity != null && this.activity.getApp() != null && params.length == 2 && params[0] instanceof String && params[1] instanceof Option) {
      final String description = (String) params[0];
      final Option<MilliSatoshi> amount_opt = (Option<MilliSatoshi>) params[1];
      return this.activity.getApp().generatePaymentRequest(description, amount_opt);
    } else {
      Log.w(TAG, "could not generate Payment Request, incorrect parameters");
      return null;
    }
  }

  protected void onPostExecute(PaymentRequest result) {
    delegate.processLightningPaymentRequest(result);
  }
}
