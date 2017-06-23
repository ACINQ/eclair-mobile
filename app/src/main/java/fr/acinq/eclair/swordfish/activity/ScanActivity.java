package fr.acinq.eclair.swordfish.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.List;

import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.utils.Validators;

public class ScanActivity extends Activity {

  public static final String EXTRA_SCAN_TYPE = "fr.acinq.eclair.swordfish.EXTRA_SCAN_TYPE";

  public static final String TYPE_INVOICE = "INVOICE";
  public static final String TYPE_URI = "URI";
  private static final String TAG = ScanActivity.class.getSimpleName();
  private DecoratedBarcodeView mBarcodeView;
  private boolean isInvoice = false;

  private BarcodeCallback callback = new BarcodeCallback() {
    @Override
    public void barcodeResult(BarcodeResult result) {
      mBarcodeView.pause();
      String scan = result.getText();
      if (scan == null || scan.trim().length() == 0) {
        mBarcodeView.setStatusText("Invalid Invoice");
        mBarcodeView.resume();
        return;
      }

      mBarcodeView.setStatusText(scan);

      if (isInvoice && canParseInvoice(scan)) {
        Intent intent = new Intent(getBaseContext(), CreatePaymentActivity.class);
        intent.putExtra(CreatePaymentActivity.EXTRA_INVOICE, scan);
        startActivity(intent);
        return;
      }
      if (!isInvoice && canParseURI(scan)) {
        Intent intent = new Intent(getBaseContext(), OpenChannelActivity.class);
        intent.putExtra(OpenChannelActivity.EXTRA_NEW_HOST_URI, scan);
        startActivity(intent);
        return;
      }

      mBarcodeView.resume();
    }

    @Override
    public void possibleResultPoints(List<ResultPoint> resultPoints) {
    }
  };

  private boolean canParseInvoice(String scan) {
    try {
      PaymentRequest.read(scan);
      return true;
    } catch (Throwable t) {
      mBarcodeView.setStatusText("Invalid Invoice");
    }
    return false;
  }

  private boolean canParseURI(String scan) {
    if (!Validators.HOST_REGEX.matcher(scan).matches()) {
      mBarcodeView.setStatusText("Invalid URI");
      return false;
    }
    return true;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_scan);

    Intent intent = getIntent();
    String type = intent.getStringExtra(EXTRA_SCAN_TYPE);
    if (TYPE_INVOICE.equals(type)) {
      isInvoice = true;
    } else if (TYPE_URI.equals(type)) {
      isInvoice = false;
    } else {
      Log.w(TAG, "Invalid Requested Type: " + type);
      startActivity(new Intent(this, HomeActivity.class));
    }

    mBarcodeView = (DecoratedBarcodeView) findViewById(R.id.scanview);
    mBarcodeView.decodeContinuous(callback);
  }

  @Override
  protected void onResume() {
    super.onResume();
    mBarcodeView.resume();
  }

  @Override
  protected void onPause() {
    super.onPause();
    mBarcodeView.pause();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    return mBarcodeView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
  }
}
