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

import android.os.AsyncTask;
import android.util.Log;

import fr.acinq.eclair.payment.PaymentRequest;

public class LNInvoiceReaderTask extends AsyncTask<String, Integer, PaymentRequest> {

  private static final String TAG = "LNInvoiceReaderTask";
  private final String invoiceAsString;
  private final AsyncInvoiceReaderTaskResponse delegate;

  public LNInvoiceReaderTask(AsyncInvoiceReaderTaskResponse delegate, String invoiceAsString) {
    this.delegate = delegate;
    this.invoiceAsString = invoiceAsString;
  }

  @Override
  protected PaymentRequest doInBackground(String... params) {
    PaymentRequest extract = null;
    try {
      extract = PaymentRequest.read(invoiceAsString);
    } catch (Throwable t) {
      Log.d(TAG, "Could not read Lightning invoice " + invoiceAsString + " with cause: " + t.getMessage());
    }
    return extract;
  }

  protected void onPostExecute(PaymentRequest result) {
    delegate.processLNInvoiceFinish(result);
  }

  public interface AsyncInvoiceReaderTaskResponse {
    void processLNInvoiceFinish(PaymentRequest output);
  }
}
