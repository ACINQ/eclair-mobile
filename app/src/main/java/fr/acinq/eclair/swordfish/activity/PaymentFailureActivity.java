package fr.acinq.eclair.swordfish.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.customviews.CoinAmountView;

public class PaymentFailureActivity extends AppCompatActivity {

  public static final String EXTRA_PAYMENTFAILURE_AMOUNT = "fr.acinq.eclair.swordfish.EXTRA_PAYMENTFAILURE_AMOUNT";
  public static final String EXTRA_PAYMENTFAILURE_DESC = "fr.acinq.eclair.swordfish.EXTRA_PAYMENTFAILURE_DESC";
  public static final String EXTRA_PAYMENTFAILURE_CAUSE = "fr.acinq.eclair.swordfish.EXTRA_PAYMENTFAILURE_CAUSE";
  private static final String TAG = "PaymentFailureActivity";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_payment_failure);

    Intent intent = getIntent();
    Long amount = intent.getLongExtra(EXTRA_PAYMENTFAILURE_AMOUNT, 0);
    String desc = intent.getStringExtra(EXTRA_PAYMENTFAILURE_DESC);
    String cause = intent.getStringExtra(EXTRA_PAYMENTFAILURE_CAUSE);

    CoinAmountView amountView = (CoinAmountView) findViewById(R.id.paymentfailure_amount);
    amountView.setAmountMsat(new MilliSatoshi(amount));
    TextView descView = (TextView) findViewById(R.id.paymentfailure_desc);
    descView.setText(desc);
    TextView causeView = (TextView) findViewById(R.id.paymentfailure_cause);
    causeView.setText(cause);
  }

  public void failure_dismiss(View view) {
    finish();
  }
}
