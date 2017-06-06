package fr.acinq.eclair.swordfish.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
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
import fr.acinq.eclair.swordfish.SendPaymentTask;
import fr.acinq.eclair.swordfish.customviews.CoinAmountView;
import fr.acinq.eclair.swordfish.model.Payment;

public class CreatePaymentActivity extends Activity implements SendPaymentTask.AsyncSendPaymentResponse {

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
    new SendPaymentTask(this, getApplicationContext(), this.currentPR).execute();
    this.findViewById(R.id.payment__layout_buttons).setVisibility(View.GONE);
    this.findViewById(R.id.payment__layout_feedback).setVisibility(View.VISIBLE);
  }

  @Override
  public void processFinish(SendPaymentTask.PaymentFeedback output) {
    if (output.hasSucceeded()) {
      Toast.makeText(this, "Payment has been sent.", Toast.LENGTH_SHORT).show();
      goToHome();
    } else {
      TextView feedbackView = (TextView) this.findViewById(R.id.payment__value_feedback);
      feedbackView.setText(output.getMessage());
      feedbackView.setTextColor(Color.RED);
    }
  }
}
