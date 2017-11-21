package fr.acinq.eclair.wallet.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.util.AsyncExecutor;

import java.net.InetSocketAddress;

import akka.dispatch.OnComplete;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.MilliBtc;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.io.Switchboard;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.events.LNNewChannelFailureEvent;
import fr.acinq.eclair.wallet.events.LNNewChannelOpenedEvent;
import fr.acinq.eclair.wallet.utils.Validators;
import scala.math.BigDecimal;

public class OpenChannelActivity extends EclairActivity {

  public static final String EXTRA_NEW_HOST_URI = "fr.acinq.eclair.swordfish.NEW_HOST_URI";
  private static final String TAG = "OpenChannelActivity";

  private TextView mCapacityHint;
  private EditText mCapacityValue;
  private TextView mPubkeyTextView;
  private TextView mIPTextView;
  private TextView mPortTextView;
  private Button mOpenButton;
  private View mErrorView;
  private TextView mErrorValue;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_open_channel);

    mOpenButton = findViewById(R.id.openchannel_do_open);
    mIPTextView = findViewById(R.id.openchannel_ip);
    mPortTextView = findViewById(R.id.openchannel_port);
    mPubkeyTextView = findViewById(R.id.openchannel_pubkey);
    mErrorView = findViewById(R.id.openchannel_error);
    mErrorValue = findViewById(R.id.openchannel_error_value);

    mCapacityHint = findViewById(R.id.openchannel_capacity_hint);
    mCapacityValue = findViewById(R.id.openchannel_capacity_value);
    mCapacityValue.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        // toggle hint depending on amount input
        mCapacityHint.setVisibility(s == null || s.length() == 0 ? View.VISIBLE : View.GONE);
        if (s.length() > 0) {
          try {
            checkAmount(s.toString());
          } catch (Exception e) {
            Log.d(TAG, "Could not convert amount to number with cause " + e.getMessage());
            toggleError(R.string.openchannel_error_capacity_nan);
          }
        }
      }

      @Override
      public void afterTextChanged(Editable s) {
      }
    });

    Intent intent = getIntent();
    String hostURI = intent.getStringExtra(EXTRA_NEW_HOST_URI);
    setNodeURI(hostURI);
  }

  private boolean checkAmount(String amount) {
    try {
      Long parsedAmountSat = Long.parseLong(amount) * 100000;
      if (parsedAmountSat < Validators.MIN_FUNDING_SAT
        || parsedAmountSat >= Validators.MAX_FUNDING_SAT) {
        toggleError(R.string.openchannel_capacity_invalid);
        return false;
      } else if (parsedAmountSat + 100000 > app.onChainBalance.get().amount()) {
        toggleError(R.string.openchannel_capacity_notenoughfunds);
        return false;
      } else {
        mErrorView.setVisibility(View.GONE);
        return true;
      }
    } catch (NumberFormatException e) {
      toggleError(R.string.openchannel_capacity_invalid);
      return false;
    }
  }

  private void toggleError(final int errorLabelId) {
    mErrorValue.setText(errorLabelId);
    mErrorView.setVisibility(View.VISIBLE);
  }

  private void setNodeURI(String uri) {
    if (Validators.HOST_REGEX.matcher(uri).matches()) {
      String[] uriArray = uri.split("@", 2);
      if (uriArray.length == 2) {
        String pubkey = uriArray[0];
        String host = uriArray[1];
        String[] hostArray = host.split(":", 2);
        if (hostArray.length == 2) {
          String ip = hostArray[0];
          String port = hostArray[1];

          mPubkeyTextView.setText(pubkey);
          mIPTextView.setText(ip);
          mPortTextView.setText(port);
          mOpenButton.setVisibility(View.VISIBLE);
          return;
        }
      }
    }
    toggleError(R.string.openchannel_error_address);
    mOpenButton.setEnabled(false);
    mCapacityValue.setEnabled(false);
    mOpenButton.setAlpha(0.3f);
  }

  public void cancelOpenChannel(View view) {
    goToHome();
  }

  private void goToHome() {
    finish();
  }

  public void confirmOpenChannel(View view) {
    if (!checkAmount(mCapacityValue.getText().toString())) return;

    mOpenButton.setVisibility(View.GONE);
    final String pubkeyString = mPubkeyTextView.getText().toString();
    final String amountString = mCapacityValue.getText().toString();
    final String ipString = mIPTextView.getText().toString();
    final String portString = mPortTextView.getText().toString();

    AsyncExecutor.create().execute(
      new AsyncExecutor.RunnableEx() {
        @Override
        public void run() throws Exception {

          final BinaryData pubkeyBinary = BinaryData.apply(pubkeyString);
          final Crypto.Point pubkeyPoint = new Crypto.Point(Crypto.curve().getCurve().decodePoint(package$.MODULE$.binaryData2array(pubkeyBinary)));
          final Crypto.PublicKey pubkey = new Crypto.PublicKey(pubkeyPoint, true);
          final Satoshi fundingSat = package$.MODULE$.millibtc2satoshi(new MilliBtc(BigDecimal.exact(amountString)));

          final InetSocketAddress address = new InetSocketAddress(ipString, Integer.parseInt(portString));
          OnComplete<Object> onComplete = new OnComplete<Object>() {
            @Override
            public void onComplete(Throwable throwable, Object o) throws Throwable {
              if (throwable != null) {
                EventBus.getDefault().post(new LNNewChannelFailureEvent(throwable.getMessage()));
              } else {
                EventBus.getDefault().post(new LNNewChannelOpenedEvent(pubkeyString));
              }
            }
          };
          app.openChannel(30, onComplete, pubkey, address,
            new Switchboard.NewChannel(fundingSat, new MilliSatoshi(0), scala.Option.apply(null)));
        }
      });
    goToHome();
  }
}
