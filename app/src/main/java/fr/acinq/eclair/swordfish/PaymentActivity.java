package fr.acinq.eclair.swordfish;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.Date;

import fr.acinq.eclair.swordfish.model.Payment;
import fr.acinq.eclair.swordfish.model.PaymentRequest;

public class PaymentActivity extends AppCompatActivity {

  private PaymentRequest currentPR = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_payment);
  }

  public void openScan(View view) {
    IntentIntegrator integrator = new IntentIntegrator(this);
    integrator.setOrientationLocked(false);
    integrator.setCaptureActivity(ScanActivity.class);
    integrator.initiateScan();
    new IntentIntegrator(this).initiateScan();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Log.e("PaymentActivity", "Got a Result Activity with code " + requestCode + "/" + resultCode);
    IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
    if (result != null /*&& requestCode == */ && resultCode == RESULT_OK) {
      if (result.getContents() == null) {
        Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show();
      } else {
        Toast.makeText(this, "Scanned: " + result.getContents(), Toast.LENGTH_LONG).show();

        TextView v_pubkey = (TextView) findViewById(R.id.payment__value_pr_pubkey);
        TextView v_amount = (TextView) findViewById(R.id.payment__value_pr_amount);
        TextView v_paymentHash = (TextView) findViewById(R.id.payment__value_pr_paymenthash);
        EditText i_paymentRequest = (EditText) findViewById(R.id.payment__input_pr);

        try {
          PaymentRequest extract = PaymentRequest.read(result.getContents());
          v_pubkey.setText(extract.nodeId);
          v_amount.setText(extract.amountMsat.toString());
          v_paymentHash.setText(extract.paymentHash);
          i_paymentRequest.setText(result.getContents());
          currentPR = extract;
        } catch (Throwable t) {
          v_pubkey.setText("N/A");
          v_amount.setText("0");
          v_paymentHash.setText("N/A");
          i_paymentRequest.setText("");
          currentPR = null;
        }
      }
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  public void sendPayment(View view) {
    try {
      Payment p = new Payment(PaymentRequest.write(currentPR),
        "lorem ipsum", new Date(), new Date());
      p.save();
      Intent intent = new Intent(this, ChannelActivity.class);
      Toast.makeText(this, "Added new Payment", Toast.LENGTH_SHORT).show();
      startActivity(intent);
      currentPR = null;
    } catch (Exception e) {
      Log.e("PaymentActivity", "Could not parse Payment Request", e);
      Toast.makeText(this, "Payment Failed", Toast.LENGTH_SHORT).show();
    }
  }
}
