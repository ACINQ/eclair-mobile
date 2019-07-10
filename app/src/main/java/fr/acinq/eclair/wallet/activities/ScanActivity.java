/*
 * Copyright 2019 ACINQ SAS
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
import androidx.databinding.DataBindingUtil;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.view.KeyEvent;
import android.view.View;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.Intents;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityScanBinding;
import fr.acinq.eclair.wallet.utils.Constants;

public class ScanActivity extends Activity {

  public static final String EXTRA_SCAN_TYPE = BuildConfig.APPLICATION_ID + ".EXTRA_SCAN_TYPE";
  public static final String TYPE_INVOICE = "INVOICE";
  public static final String TYPE_URI_OPEN = "URI_OPEN";
  public static final String TYPE_URI_CONNECT = "URI_CONNECT";
  private final Logger log = LoggerFactory.getLogger(ScanActivity.class);
  private ActivityScanBinding mBinding;
  private String scanType = "";

  private BarcodeCallback callback = new BarcodeCallback() {
    @Override
    public void barcodeResult(BarcodeResult result) {
      final String scannedText = result.getText();
      if (scannedText != null) {
        if (TYPE_INVOICE.equals(scanType)) {
          Intent intent = new Intent(getBaseContext(), SendPaymentActivity.class);
          intent.putExtra(SendPaymentActivity.EXTRA_INVOICE, scannedText);
          startActivity(intent);
        } else if (TYPE_URI_OPEN.equals(scanType)) {
          Intent intent = new Intent(getBaseContext(), OpenChannelActivity.class);
          intent.putExtra(OpenChannelActivity.EXTRA_NEW_HOST_URI, scannedText);
          startActivity(intent);
        } else if (TYPE_URI_CONNECT.equals(scanType)) {
          setResult(RESULT_OK, new Intent().putExtra(OpenChannelActivity.EXTRA_NEW_HOST_URI, scannedText));
        }
        finish();
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
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_scan);

    final Intent barcodeIntent = new Intent();
    barcodeIntent.putExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN);
    barcodeIntent.putExtra(Intents.Scan.FORMATS, BarcodeFormat.QR_CODE.toString());
    mBinding.scanView.getStatusView().setVisibility(View.GONE);
    mBinding.scanView.initializeFromIntent(barcodeIntent);

    scanType = getIntent().getStringExtra(EXTRA_SCAN_TYPE);
    if (TYPE_INVOICE.equals(scanType)) {
      mBinding.scanTitle.setText(R.string.scan_title_invoice);
    } else if (TYPE_URI_OPEN.equals(scanType) || TYPE_URI_CONNECT.equals(scanType)) {
      mBinding.scanTitle.setText(R.string.scan_title_ln_uri);
    } else {
      log.error("invalid scan requested scanType {}", scanType);
      finish();
    }

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, Constants.PERMISSION_CAMERA_REQUEST);
    } else {
      startScanning();
    }
  }

  private void startScanning() {
    mBinding.scanView.decodeContinuous(callback);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
    if (requestCode == Constants.PERMISSION_CAMERA_REQUEST) {
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
    mBinding.scanView.resume();
  }

  @Override
  protected void onPause() {
    super.onPause();
    mBinding.scanView.pause();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    return mBinding.scanView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
  }
}
