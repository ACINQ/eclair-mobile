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

import org.bitcoinj.uri.BitcoinURI;


public class BitcoinInvoiceReaderTask extends AsyncTask<String, Integer, BitcoinURI> {

  private static final String TAG = "BitcoinInvoiceReader";
  private final String invoiceAsString;
  private final AsyncInvoiceReaderTaskResponse delegate;

  public BitcoinInvoiceReaderTask(AsyncInvoiceReaderTaskResponse delegate, String invoiceAsString) {
    this.delegate = delegate;
    this.invoiceAsString = invoiceAsString;
  }

  @Override
  protected BitcoinURI doInBackground(String... params) {
    BitcoinURI extract = null;
    try {
      if (invoiceAsString.toLowerCase().startsWith("bitcoin:")) {
        extract = new BitcoinURI(invoiceAsString);
      } else {
        // to handle raw address
        extract = new BitcoinURI("bitcoin:" + invoiceAsString);
      }
    } catch (Throwable t) {
      Log.d(TAG, "Could not read Bitcoin invoice " + invoiceAsString + " with cause: " + t.getMessage());
    }
    return extract;
  }

  protected void onPostExecute(BitcoinURI result) {
    delegate.processBitcoinInvoiceFinish(result);
  }

  public interface AsyncInvoiceReaderTaskResponse {
    void processBitcoinInvoiceFinish(BitcoinURI output);
  }
}
