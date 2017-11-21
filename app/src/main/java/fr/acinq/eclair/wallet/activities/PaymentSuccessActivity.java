package fr.acinq.eclair.wallet.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AnticipateOvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import fr.acinq.eclair.wallet.R;

public class PaymentSuccessActivity extends EclairActivity {

  public static final String EXTRA_PAYMENTSUCCESS_AMOUNT = "fr.acinq.eclair.wallet.EXTRA_PAYMENTSUCCESS_AMOUNT";
  public static final String EXTRA_PAYMENTSUCCESS_DESC = "fr.acinq.eclair.wallet.EXTRA_PAYMENTSUCCESS_DESC";
  private static final String TAG = "PaymentSuccessActivity";

  private Handler dismissHandler;
  private ImageView mCircleImage;
  private ImageView mCheckImage;
  private TextView mDescView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_payment_success);

    Intent intent = getIntent();
    String desc = intent.getStringExtra(EXTRA_PAYMENTSUCCESS_DESC);

    mDescView = findViewById(R.id.paymentsuccess_desc);
    mCircleImage = findViewById(R.id.paymentsuccess_circle);
    mCheckImage = findViewById(R.id.paymentsuccess_check);
    mDescView.setText(desc);
    tada();

    dismissHandler = new Handler();
    dismissHandler.postDelayed(new Runnable() {
      public void run() {
        finish();
      }
    }, 3500);
  }

  private void tada() {
    mCircleImage.setAlpha(0f);
    mCircleImage.setScaleX(0.3f);
    mCircleImage.setScaleY(0.3f);

    mCheckImage.setAlpha(0f);
    mCheckImage.setScaleX(0.6f);
    mCheckImage.setScaleY(0.6f);

    mCircleImage.animate().alpha(1).scaleY(1).scaleX(1).setStartDelay(80).setInterpolator(new AnticipateOvershootInterpolator()).setDuration(350).start();
    mCheckImage.animate().alpha(1).scaleY(1).scaleX(1).setStartDelay(120).setInterpolator(new AnticipateOvershootInterpolator()).setDuration(500).start();
  }

  public void success_tap(View view) {
    tada();
  }

  public void success_dismiss(View view) {
    dismissHandler.removeCallbacks(null);
    finish();
  }
}
