package fr.acinq.eclair.wallet.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

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

public class OpenChannelActivity extends EclairModalActivity {

  public static final String EXTRA_NEW_HOST_URI = "fr.acinq.eclair.swordfish.NEW_HOST_URI";

  private EditText mAmountEdit;
  private TextView mPubkeyTextView;
  private TextView mIPTextView;
  private TextView mPortTextView;
  private Button mOpenButton;
  private TextView mErrorAView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_open_channel);

    mOpenButton = (Button) findViewById(R.id.openchannel_do_open);
    mIPTextView = (TextView) findViewById(R.id.openchannel_ip);
    mPortTextView = (TextView) findViewById(R.id.openchannel_port);
    mPubkeyTextView = (TextView) findViewById(R.id.openchannel_pubkey);
    mErrorAView = (TextView) findViewById(R.id.openchannel_error);

    mAmountEdit = (EditText) findViewById(R.id.openchannel_capacity);
    mAmountEdit.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (s.length() > 0) {
          checkAmount(s.toString());
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
        mAmountEdit.setTextColor(getColor(R.color.red));
        mErrorAView.setText(R.string.openchannel_capacity_invalid);
        mErrorAView.setVisibility(View.VISIBLE);
        return false;
      } else if (parsedAmountSat + 100000 > app.getWalletBalanceSat().getValue()) {
        mAmountEdit.setTextColor(getColor(R.color.red));
        mErrorAView.setText(R.string.openchannel_capacity_notenoughfunds);
        mErrorAView.setVisibility(View.VISIBLE);
        return false;
      } else {
        mAmountEdit.setTextColor(getColor(R.color.colorGrey_4));
        mErrorAView.setVisibility(View.GONE);
        return true;
      }
    } catch (NumberFormatException e) {
      mErrorAView.setText(R.string.openchannel_capacity_invalid);
      mAmountEdit.setTextColor(getColor(R.color.red));
      mErrorAView.setVisibility(View.VISIBLE);
      return false;
    }
  }

  private void setNodeURI(String uri) {
    if (!Validators.HOST_REGEX.matcher(uri).matches()) {
      Toast.makeText(this, R.string.openchannel_invalid, Toast.LENGTH_LONG).show();
      goToHome();
    } else {
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
        }
      }
    }
  }

  public void cancelOpenChannel(View view) {
    goToHome();
  }

  private void goToHome() {
    finish();
  }

  public void confirmOpenChannel(View view) {
    if (!checkAmount(mAmountEdit.getText().toString())) return;

    mOpenButton.setVisibility(View.GONE);
    final String pubkeyString = mPubkeyTextView.getText().toString();
    final String amountString = mAmountEdit.getText().toString();
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
