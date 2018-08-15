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

package fr.acinq.eclair.wallet.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.List;

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.utils.Constants;

public class ScanActivity extends Activity {

  public static final String EXTRA_SCAN_TYPE = "fr.acinq.eclair.wallet.EXTRA_SCAN_TYPE";

  public static final String TYPE_INVOICE = "INVOICE";
  public static final String TYPE_URI = "URI";
  private static final String TAG = ScanActivity.class.getSimpleName();
  private DecoratedBarcodeView mBarcodeView;
  private TextView mScannedValue;
  private TextView mScanTitle;
  private boolean isInvoice = false;

  private BarcodeCallback callback = new BarcodeCallback() {
    @Override
    public void barcodeResult(BarcodeResult result) {
      final String scan = result.getText();
      if (scan != null) {
        mBarcodeView.pause();
        mScannedValue.setVisibility(View.VISIBLE);
        mScannedValue.setText(scan);

        final Handler dismissHandler = new Handler();
        dismissHandler.postDelayed(() -> {
          if (isInvoice) {
            Intent intent = new Intent(getBaseContext(), SendPaymentActivity.class);
            intent.putExtra(SendPaymentActivity.EXTRA_INVOICE, scan);
            startActivity(intent);
          } else {
            Intent intent = new Intent(getBaseContext(), OpenChannelActivity.class);
            intent.putExtra(OpenChannelActivity.EXTRA_NEW_HOST_URI, scan);
            startActivity(intent);
          }
          finish();
        }, 500);

        return;
      }
    }

    @Override
    public void possibleResultPoints(List<ResultPoint> resultPoints) {
    }
  };

  public void scanCancel(View view) {
    finish();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_scan);
    mBarcodeView = findViewById(R.id.scan_view);
    mBarcodeView.getStatusView().setVisibility(View.GONE);
    mScannedValue = findViewById(R.id.scan_value);
    mScanTitle = findViewById(R.id.scan_title);

    final Intent intent = getIntent();
    final String type = intent.getStringExtra(EXTRA_SCAN_TYPE);
    if (TYPE_INVOICE.equals(type)) {
      isInvoice = true;
      mScanTitle.setText(R.string.scan_title_invoice);
    } else if (TYPE_URI.equals(type)) {
      mScanTitle.setText(R.string.scan_title_ln_uri);
      isInvoice = false;
    } else {
      Log.w(TAG, "Invalid Requested Type: " + type);
      finish();
    }

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, Constants.CAMERA_PERMISSION_REQUEST);
    } else {
      startScanning();
    }
  }

  private void startScanning() {
    mBarcodeView.decodeContinuous(callback);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
    if (requestCode == Constants.CAMERA_PERMISSION_REQUEST) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        startScanning();
      } else {
        finish();
      }
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
