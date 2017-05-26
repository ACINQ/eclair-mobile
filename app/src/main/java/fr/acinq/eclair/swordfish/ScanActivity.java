package fr.acinq.eclair.swordfish;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

public class ScanActivity extends CaptureActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_scan);
  }
  @Override
  protected DecoratedBarcodeView initializeContent() {
    setContentView(R.layout.activity_scan);
    return (DecoratedBarcodeView)findViewById(R.id.zxing_barcode_scanner);
  }
}
