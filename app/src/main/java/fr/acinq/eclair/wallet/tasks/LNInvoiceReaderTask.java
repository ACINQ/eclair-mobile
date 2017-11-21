package fr.acinq.eclair.wallet.tasks;

import android.os.AsyncTask;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

import fr.acinq.eclair.payment.PaymentRequest;

public class LNInvoiceReaderTask extends AsyncTask<String, Integer, PaymentRequest> {

  private static final String TAG = "LNInvoiceReaderTask";
  private final String invoiceAsString;
  private final AsyncInvoiceReaderTaskResponse delegate;
  private final static List<String> LIGHTNING_PREFIXES = Arrays.asList("lightning:", "lightning://");

  public LNInvoiceReaderTask(AsyncInvoiceReaderTaskResponse delegate, String invoiceAsString) {
    this.delegate = delegate;
    this.invoiceAsString = invoiceAsString;
  }

  @Override
  protected PaymentRequest doInBackground(String... params) {
    PaymentRequest extract = null;
    try {
      for (String prefix : LIGHTNING_PREFIXES) {
        if (extract == null && invoiceAsString.startsWith(prefix)) {
          extract = PaymentRequest.read(invoiceAsString.substring(prefix.length()));
        }
      }
      if (extract == null) {
        extract = PaymentRequest.read(invoiceAsString);
      }
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
