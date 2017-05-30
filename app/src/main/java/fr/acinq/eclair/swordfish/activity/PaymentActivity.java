package fr.acinq.eclair.swordfish.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.Date;

import akka.actor.ActorRef;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.payment.SendPayment;
import fr.acinq.eclair.swordfish.EclairHelper;
import fr.acinq.eclair.swordfish.fragment.OneInputDialog;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.model.Payment;
import fr.acinq.eclair.swordfish.model.PaymentRequest;

public class PaymentActivity extends FragmentActivity implements OneInputDialog.OneInputDialogListener {

  private PaymentRequest currentPR = PaymentRequest.read("03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134:1000000:dcdf28bfa9aef286b31cd627bb1a0f68d7dcaa5080df6b24f3b30fdc662cecd1");

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_payment);
  }

  public void payment__openScan(View view) {
    IntentIntegrator integrator = new IntentIntegrator(this);
    integrator.setOrientationLocked(false);
    integrator.setCaptureActivity(ScanActivity.class);
    integrator.initiateScan();
    new IntentIntegrator(this).initiateScan();
  }

  public void payment__showManualPRDialog(View view) {
    OneInputDialog dialog = new OneInputDialog();
    dialog.show(getFragmentManager(), "PRDialog");
  }

  @Override
  public void onDialogPositiveClick(OneInputDialog dialog, String pr) {
    setPaymentRequest(pr);
  }

  private void setPaymentRequest(String prString) {
    TextView v_pubkey = (TextView) findViewById(R.id.payment__value_pr_pubkey);
    TextView v_amount = (TextView) findViewById(R.id.payment__value_pr_amount);
    TextView v_paymentHash = (TextView) findViewById(R.id.payment__value_pr_paymenthash);
    try {
      PaymentRequest extract = PaymentRequest.read(prString);
      v_pubkey.setText(extract.nodeId);
      v_amount.setText(extract.amountMsat.toString());
      v_paymentHash.setText(extract.paymentHash);
      currentPR = extract;
    } catch (Throwable t) {
      v_pubkey.setText("N/A");
      v_amount.setText("0");
      v_paymentHash.setText("N/A");
      currentPR = null;
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Log.d("PaymentActivity", "Got a Result Activity with code " + requestCode + "/" + resultCode);
    IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
    if (result != null /*&& requestCode == */ && resultCode == RESULT_OK) {
      if (result.getContents() == null) {
        Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show();
      } else {
        Toast.makeText(this, "Scanned: " + result.getContents(), Toast.LENGTH_LONG).show();
        setPaymentRequest(result.getContents());
      }
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  public void sendPayment(View view) {
    try {
      EditText i_desc = (EditText) findViewById(R.id.payment__input_desc);
      Payment p = new Payment(PaymentRequest.write(currentPR),
        i_desc.getText().toString(), new Date(), new Date());
      p.save();
      ActorRef pi = EclairHelper.getInstance(this).getSetup().paymentInitiator();

      BinaryData paymentHash = BinaryData.apply(currentPR.paymentHash);
      BinaryData nodeId = BinaryData.apply(currentPR.nodeId);
      Crypto.Point pointNodeId = new Crypto.Point(Crypto.curve().getCurve().decodePoint(package$.MODULE$.binaryData2array(nodeId)));
      Crypto.PublicKey publicKey = new Crypto.PublicKey(pointNodeId, true);

      pi.tell(new SendPayment(currentPR.amountMsat, paymentHash, publicKey, 5), pi);
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
