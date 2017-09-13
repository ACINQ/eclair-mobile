package fr.acinq.eclair.wallet.tasks;

import android.os.AsyncTask;
import android.util.Log;

import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.uri.BitcoinURIParseException;

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
