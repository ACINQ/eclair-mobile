package fr.acinq.eclair.swordfish.tasks;

import android.os.AsyncTask;
import android.util.Log;

import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.uri.BitcoinURIParseException;

import fr.acinq.eclair.payment.PaymentRequest;

public class InvoiceReaderTask extends AsyncTask<String, Integer, PaymentRequest> {

  private static final String TAG = "InvoiceReaderTask";
  private final String invoiceAsString;
  private final AsyncInvoiceReaderTaskResponse delegate;

  public InvoiceReaderTask(AsyncInvoiceReaderTaskResponse delegate, String invoiceAsString) {
    this.delegate = delegate;
    this.invoiceAsString = invoiceAsString;
  }

  @Override
  protected PaymentRequest doInBackground(String... params) {
    PaymentRequest extract = null;
    try {
      extract = PaymentRequest.read(invoiceAsString);
    } catch (Throwable t) {
      Log.e(TAG, "Could not read invoice " + invoiceAsString, t);
      try {
        BitcoinURI bitcoinURI = new BitcoinURI(invoiceAsString);
      } catch (BitcoinURIParseException e) {
        Log.e(TAG, "Could not read Bitcoin URI", e);
        Log.i(TAG, "Invoice is neither a LN nor a Bitcoin invoice");
      }
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
