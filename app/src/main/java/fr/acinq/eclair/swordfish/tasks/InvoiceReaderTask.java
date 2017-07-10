package fr.acinq.eclair.swordfish.tasks;

import android.os.AsyncTask;
import android.util.Log;

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
    }
    return extract;
  }

  protected void onPostExecute(PaymentRequest result) {
    delegate.processFinish(result);
  }

  public interface AsyncInvoiceReaderTaskResponse {
    void processFinish(PaymentRequest output);
  }
}
