package fr.acinq.eclair.wallet.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.KeyEvent;

import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.List;

import fr.acinq.eclair.wallet.R;

public class ScanActivity extends Activity {

  public static final String EXTRA_SCAN_TYPE = "fr.acinq.eclair.wallet.EXTRA_SCAN_TYPE";

  public static final String TYPE_INVOICE = "INVOICE";
  public static final String TYPE_URI = "URI";
  private static final String TAG = ScanActivity.class.getSimpleName();
  private static int MY_PERMISSIONS_REQUEST_CAMERA = 0;
  private DecoratedBarcodeView mBarcodeView;
  private boolean isInvoice = false;

  private BarcodeCallback callback = new BarcodeCallback() {
    @Override
    public void barcodeResult(BarcodeResult result) {
      String scan = result.getText();
      if (scan != null) {
        mBarcodeView.pause();
        mBarcodeView.setStatusText(scan);

        if (isInvoice) {
          Intent intent = new Intent(getBaseContext(), CreatePaymentActivity.class);
          intent.putExtra(CreatePaymentActivity.EXTRA_INVOICE, scan);
          startActivity(intent);
        } else {
          Intent intent = new Intent(getBaseContext(), OpenChannelActivity.class);
          intent.putExtra(OpenChannelActivity.EXTRA_NEW_HOST_URI, scan);
          startActivity(intent);
        }
        finish();
        return;
      }
    }

    @Override
    public void possibleResultPoints(List<ResultPoint> resultPoints) {
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_scan);
    mBarcodeView = (DecoratedBarcodeView) findViewById(R.id.scanview);

    Intent intent = getIntent();
    String type = intent.getStringExtra(EXTRA_SCAN_TYPE);
    if (TYPE_INVOICE.equals(type)) {
      isInvoice = true;
    } else if (TYPE_URI.equals(type)) {
      isInvoice = false;
    } else {
      Log.w(TAG, "Invalid Requested Type: " + type);
      finish();
    }

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, MY_PERMISSIONS_REQUEST_CAMERA);
    } else {
      startScanning();
    }
  }

  private void startScanning() {
    mBarcodeView.decodeContinuous(callback);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
    if (requestCode == MY_PERMISSIONS_REQUEST_CAMERA) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        startScanning();
      } else {
        finish();
      }
      return;
    }
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
