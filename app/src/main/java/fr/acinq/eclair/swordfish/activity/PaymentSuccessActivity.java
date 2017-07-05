package fr.acinq.eclair.swordfish.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import fr.acinq.eclair.swordfish.R;

public class PaymentSuccessActivity extends AppCompatActivity {

  public static final String EXTRA_PAYMENTSUCCESS_AMOUNT = "fr.acinq.eclair.swordfish.EXTRA_PAYMENTSUCCESS_AMOUNT";
  public static final String EXTRA_PAYMENTSUCCESS_DESC = "fr.acinq.eclair.swordfish.EXTRA_PAYMENTSUCCESS_DESC";
  private static final String TAG = "PaymentSuccessActivity";

  private Handler dismissHandler;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_payment_success);

    Intent intent = getIntent();
    String desc = intent.getStringExtra(EXTRA_PAYMENTSUCCESS_DESC);

    TextView descView = (TextView) findViewById(R.id.paymentsuccess_desc);
    descView.setText(desc);

    ImageView mCircleImage = (ImageView) findViewById(R.id.paymentsuccess_circle);
    ImageView mCheckImage = (ImageView) findViewById(R.id.paymentsuccess_check);
    mCircleImage.setAlpha(0);
    mCircleImage.setTranslationY(60);
    mCheckImage.setAlpha(0);
    mCheckImage.setScaleX(0.6f);
    mCheckImage.setScaleY(0.6f);
    mCircleImage.animate().alpha(1).translationY(0).setStartDelay(200).setDuration(250).start();
    mCheckImage.animate().alpha(1).scaleY(1).scaleX(1).setStartDelay(350).setDuration(100).start();

    dismissHandler = new Handler();
    dismissHandler.postDelayed(new Runnable() {
      public void run() {
        finish();
      }
    }, 3500);
  }

  public void success_dismiss(View view) {
    dismissHandler.removeCallbacks(null);
    finish();
  }
}
