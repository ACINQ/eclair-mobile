package fr.acinq.eclair.swordfish.activity;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.customviews.CoinAmountView;

public class PaymentSuccessActivity extends AppCompatActivity {

  public static final String EXTRA_PAYMENTSUCCESS_AMOUNT = "fr.acinq.eclair.swordfish.EXTRA_PAYMENTSUCCESS_AMOUNT";
  public static final String EXTRA_PAYMENTSUCCESS_DESC = "fr.acinq.eclair.swordfish.EXTRA_PAYMENTSUCCESS_DESC";
  private static final String TAG = "PaymentSuccessActivity";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_payment_success);

    Intent intent = getIntent();
    Long amount = intent.getLongExtra(EXTRA_PAYMENTSUCCESS_AMOUNT, 0);
    String desc = intent.getStringExtra(EXTRA_PAYMENTSUCCESS_DESC);

    CoinAmountView amountView = (CoinAmountView) findViewById(R.id.paymentsuccess_amount);
    amountView.setAmountMsat(new MilliSatoshi(amount));
    TextView descView = (TextView) findViewById(R.id.paymentsuccess_desc);
    descView.setText(desc);
  }

  public void success_dismiss(View view) {
    finish();
  }
}
