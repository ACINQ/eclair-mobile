package fr.acinq.eclair.swordfish.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import fr.acinq.eclair.swordfish.R;

public class PaymentFailureActivity extends AppCompatActivity {

  public static final String EXTRA_PAYMENTFAILURE_AMOUNT = "fr.acinq.eclair.swordfish.EXTRA_PAYMENTFAILURE_AMOUNT";
  public static final String EXTRA_PAYMENTFAILURE_DESC = "fr.acinq.eclair.swordfish.EXTRA_PAYMENTFAILURE_DESC";
  public static final String EXTRA_PAYMENTFAILURE_CAUSE = "fr.acinq.eclair.swordfish.EXTRA_PAYMENTFAILURE_CAUSE";
  private static final String TAG = "PaymentFailureActivity";

  private Handler dismissHandler;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_payment_failure);

    Intent intent = getIntent();
    String desc = intent.getStringExtra(EXTRA_PAYMENTFAILURE_DESC);
    String cause = intent.getStringExtra(EXTRA_PAYMENTFAILURE_CAUSE);

    TextView descView = (TextView) findViewById(R.id.paymentfailure_desc);
    descView.setText(desc);
    TextView causeView = (TextView) findViewById(R.id.paymentfailure_cause);
    causeView.setText(cause);

    ImageView mSadImage = (ImageView) findViewById(R.id.paymentfailure_sad);
    mSadImage.setAlpha(0);
    mSadImage.animate().alpha(1).setStartDelay(250).setDuration(500).start();

    dismissHandler = new Handler();
    dismissHandler.postDelayed(new Runnable() {
      public void run() {
        finish();
      }
    }, 3500);
  }

  public void failure_dismiss(View view) {
    dismissHandler.removeCallbacks(null);
    finish();
  }
}
