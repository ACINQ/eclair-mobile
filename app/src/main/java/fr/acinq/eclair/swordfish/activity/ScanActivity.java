package fr.acinq.eclair.swordfish.activity;

import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import fr.acinq.eclair.swordfish.R;

public class ScanActivity extends CaptureActivity {

  @Override
  protected DecoratedBarcodeView initializeContent() {
    setContentView(R.layout.activity_scan);
    return (DecoratedBarcodeView) findViewById(R.id.payment__scanview);
  }
}
