package fr.acinq.eclair.wallet.tasks;

import android.os.AsyncTask;
import android.util.Log;

import org.bitcoinj.params.AbstractBitcoinNetParams;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.uri.BitcoinURIParseException;

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
      if (invoiceAsString.startsWith(AbstractBitcoinNetParams.BITCOIN_SCHEME + ":")) {
        extract = new BitcoinURI(invoiceAsString);
      } else {
        // to handle raw address
        extract = new BitcoinURI(AbstractBitcoinNetParams.BITCOIN_SCHEME + ":" + invoiceAsString);
      }
    } catch (BitcoinURIParseException e) {
      Log.e(TAG, "Could not read Bitcoin invoice " + invoiceAsString + " with cause: " + e.getMessage());
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
