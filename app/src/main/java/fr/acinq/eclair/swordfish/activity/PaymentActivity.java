package fr.acinq.eclair.swordfish.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;

import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.model.Payment;

public class PaymentActivity extends AppCompatActivity {

  private PaymentRequest currentPR = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_payment);
    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);

    Intent intent = getIntent();
    String paymentRequest = intent.getStringExtra(HomeActivity.EXTRA_PAYMENTREQUEST);
    setPaymentRequest(paymentRequest);
  }

  private void setPaymentRequest(String prString) {
    TextView v_pubkey = (TextView) findViewById(R.id.payment__value_pr_pubkey);
    TextView v_amount = (TextView) findViewById(R.id.payment__value_pr_amount);
    TextView v_paymentHash = (TextView) findViewById(R.id.payment__value_pr_paymenthash);
    try {
      PaymentRequest extract = PaymentRequest.read(prString);
      v_pubkey.setText(extract.nodeId().toString());
      v_amount.setText(new Long(extract.amount().amount() / 1000).toString()); // in satoshi
      v_paymentHash.setText(extract.paymentHash().toString());
      currentPR = extract;
    } catch (Throwable t) {
      Toast.makeText(this, "Invalid Payment Request", Toast.LENGTH_SHORT).show();
      Intent intent = new Intent(this, HomeActivity.class);
      startActivity(intent);
    }
  }



  public void sendPayment(View view) {
    try {
      Payment p = new Payment(PaymentRequest.write(currentPR),
        "not yet implemented", new Date(), new Date());
      p.save();
//      ActorRef pi = EclairHelper.getInstance(this).getSetup().paymentInitiator();
//
//      BinaryData paymentHash = BinaryData.apply(currentPR.paymentHash);
//      BinaryData nodeId = BinaryData.apply(currentPR.nodeId);
//      Crypto.Point pointNodeId = new Crypto.Point(Crypto.curve().getCurve().decodePoint(package$.MODULE$.binaryData2array(nodeId)));
//      Crypto.PublicKey publicKey = new Crypto.PublicKey(pointNodeId, true);
//
//      pi.tell(new SendPayment(currentPR.amountMsat, paymentHash, publicKey, 5), pi);
      Intent intent = new Intent(this, HomeActivity.class);
      Toast.makeText(this, "Added new Payment", Toast.LENGTH_SHORT).show();
      startActivity(intent);
      currentPR = null;
    } catch (Exception e) {
      Log.e("PaymentActivity", "Could not parse Payment Request", e);
      Toast.makeText(this, "Payment Failed", Toast.LENGTH_SHORT).show();
    }
  }
}
