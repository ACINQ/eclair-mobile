package fr.acinq.eclair.swordfish.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.util.Date;

import akka.actor.ActorRef;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.payment.SendPayment;
import fr.acinq.eclair.swordfish.EclairHelper;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.customviews.CoinAmountView;
import fr.acinq.eclair.swordfish.model.Payment;

public class CreatePaymentActivity extends Activity {

  private PaymentRequest currentPR = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_create_payment);

    Intent intent = getIntent();
    String paymentRequest = intent.getStringExtra(HomeActivity.EXTRA_PAYMENTREQUEST);
    setPaymentRequest(paymentRequest);
  }

  private void setPaymentRequest(String prString) {
    CoinAmountView v_amount = (CoinAmountView) findViewById(R.id.payment__value_amount);
    try {
      PaymentRequest extract = PaymentRequest.read(prString);
      v_amount.setAmountSat(package$.MODULE$.millisatoshi2satoshi(extract.amount()));
      currentPR = extract;
    } catch (Throwable t) {
      Toast.makeText(this, "Invalid Payment Request", Toast.LENGTH_SHORT).show();
      goToHome();
    }
  }

  public void cancelPayment(View view) {
    goToHome();
  }

  private void goToHome() {
    this.currentPR = null;
    Intent intent = new Intent(this, HomeActivity.class);
    startActivity(intent);
  }

  public void sendPayment(View view) {
    try {
      Payment p = new Payment(currentPR.paymentHash().toString(), PaymentRequest.write(currentPR),
        "Placeholder description", new Date(), new Date());
      p.save();
      ActorRef pi = EclairHelper.getInstance(this).getSetup().paymentInitiator();

      BinaryData paymentHash = BinaryData.apply(currentPR.paymentHash().toString());
      BinaryData nodeId = BinaryData.apply(currentPR.nodeId().toString());
      Crypto.Point pointNodeId = new Crypto.Point(Crypto.curve().getCurve().decodePoint(package$.MODULE$.binaryData2array(nodeId)));
      Crypto.PublicKey publicKey = new Crypto.PublicKey(pointNodeId, true);

      pi.tell(new SendPayment(currentPR.amount().amount(), paymentHash, publicKey, 5), pi);
      Toast.makeText(this, "Added new Payment", Toast.LENGTH_SHORT).show();
      goToHome();
    } catch (Exception e) {
      Log.e("CreatePaymentActivity", "Could not send payment", e);
      Toast.makeText(this, "Payment Failed", Toast.LENGTH_SHORT).show();
      goToHome();
    }
  }
}
