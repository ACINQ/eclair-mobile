package fr.acinq.eclair.swordfish.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.greenrobot.eventbus.util.ThrowableFailureEvent;

import java.util.List;

import fr.acinq.eclair.payment.PaymentRequest;
import fr.acinq.eclair.swordfish.ChannelUpdateEvent;
import fr.acinq.eclair.swordfish.EclairEventService;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.SWPaymentEvent;
import fr.acinq.eclair.swordfish.adapters.PaymentListItemAdapter;
import fr.acinq.eclair.swordfish.customviews.CoinAmountView;
import fr.acinq.eclair.swordfish.model.Payment;

public class HomeActivity extends AppCompatActivity {

  private static final String TAG = "Home Activity";
  public static final String EXTRA_PAYMENTREQUEST = "fr.acinq.eclair.swordfish.PAYMENT_REQUEST";
  public static final String EXTRA_PAYMENT_DETAILS_ID = "fr.acinq.eclair.swordfish.PAYMENT_DETAILS_ID";

  private PaymentListItemAdapter paymentAdapter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_home);

    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(false);
  }

  @Override
  public void onStart() {
    EventBus.getDefault().register(this);
    super.onStart();
    updateBalance();
    this.paymentAdapter = new PaymentListItemAdapter(this, getPayments());
    RecyclerView listView = (RecyclerView) findViewById(R.id.main__list_payments);
    listView.setHasFixedSize(true);
    listView.setLayoutManager(new LinearLayoutManager(this));
    listView.setAdapter(paymentAdapter);
  }

  @Override
  public void onStop() {
    EventBus.getDefault().unregister(this);
    super.onStop();
  }

  @Override
  public void onPause() {
    EventBus.getDefault().unregister(this);
    super.onPause();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_home, menu);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.menu_home_channelslist:
        Intent intent = new Intent(this, ChannelsListActivity.class);
        startActivity(intent);
        return true;
      case R.id.menu_home_settings:
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        startActivity(settingsIntent);
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  private List<Payment> getPayments () {
    List<Payment> list =  Payment.findWithQuery(Payment.class, "SELECT * FROM Payment ORDER BY created DESC LIMIT 100");
    TextView pending = (TextView) findViewById(R.id.pending);
    TextView emptyLabel = (TextView) findViewById(R.id.main__listview_label_empty);

    if (list.isEmpty()) {
      emptyLabel.setVisibility(View.VISIBLE);
      pending.setVisibility(View.GONE);
    } else {
      emptyLabel.setVisibility(View.GONE);
      pending.setVisibility(View.GONE);
    }
    return list;
  }

  private void updatePayments(List<Payment> payments) {

  }

  public void channel__openScan(View view) {
    IntentIntegrator integrator = new IntentIntegrator(this);
    integrator.setOrientationLocked(false);
    integrator.setCaptureActivity(ScanActivity.class);
    integrator.setBeepEnabled(false);
    integrator.initiateScan();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Log.d(TAG, "Got a Result Activity with code " + requestCode + "/" + resultCode);
    IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
    if (result != null /*&& requestCode == */ && resultCode == RESULT_OK) {
      if (result.getContents() == null) {
        Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show();
      } else {
        try {
          // read content to check if the PR is valid
          PaymentRequest extract = PaymentRequest.read(result.getContents());
          Intent intent = new Intent(this, CreatePaymentActivity.class);
          intent.putExtra(EXTRA_PAYMENTREQUEST, PaymentRequest.write(extract));
          startActivity(intent);
        } catch (Throwable t) {
          Toast.makeText(this, "Invalid Payment Request", Toast.LENGTH_SHORT).show();
        }
      }
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleFailureEvent(ThrowableFailureEvent event) {
    Toast.makeText(this, "Payment failed: " + event.getThrowable().getMessage(), Toast.LENGTH_LONG).show();
    paymentAdapter.update(getPayments());
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onMessageEvent(SWPaymentEvent event) {
    paymentAdapter.update(getPayments());
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onMessageEvent(ChannelUpdateEvent event) {
    updateBalance();
  }

  private void updateBalance() {
    CoinAmountView aggregatedBalanceView = (CoinAmountView) findViewById(R.id.channel__value_balance);
    aggregatedBalanceView.setAmountSat(EclairEventService.getTotalBalance());
  }
}
