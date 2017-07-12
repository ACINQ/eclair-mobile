package fr.acinq.eclair.swordfish.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
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
import fr.acinq.eclair.swordfish.App;
import fr.acinq.eclair.swordfish.EclairHelper;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.events.LNNewChannelFailureEvent;
import fr.acinq.eclair.swordfish.events.LNNewChannelOpenedEvent;
import fr.acinq.eclair.swordfish.fragments.OneInputDialog;
import fr.acinq.eclair.swordfish.utils.Validators;
import scala.math.BigDecimal;

public class OpenChannelActivity extends Activity implements OneInputDialog.OneInputDialogListener {

  public static final String EXTRA_NEW_HOST_URI = "fr.acinq.eclair.swordfish.NEW_HOST_URI";

  private EditText mAmountEdit;
  private TextView mPubkeyTextView;
  private TextView mIPTextView;
  private TextView mPortTextView;
  private Button mOpenButton;
  private EclairHelper eclairHelper;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    eclairHelper = ((App) getApplication()).getEclairInstance();
    setContentView(R.layout.activity_open_channel);

    mOpenButton = (Button) findViewById(R.id.fund__button_openchannel);
    mIPTextView = (TextView) findViewById(R.id.fund__value_uri_ip);
    mPortTextView = (TextView) findViewById(R.id.fund__value_uri_port);
    mPubkeyTextView = (TextView) findViewById(R.id.fund__value_uri_pubkey);

    mAmountEdit = (EditText) findViewById(R.id.fund__input_amount);
    mAmountEdit.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (s.length() > 0) {
          try {
            Long parsedAmountSat = Long.parseLong(s.toString()) * 100000;
            if (parsedAmountSat < Validators.MIN_FUNDING_SAT || parsedAmountSat >= Validators.MAX_FUNDING_SAT) {
              mAmountEdit.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.colorPrimary));
            } else {
              mAmountEdit.setBackgroundColor(Color.TRANSPARENT);
            }
          } catch (NumberFormatException e) {
            goToHome();
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

  @Override
  protected void onStart() {
    super.onStart();
  }

  @Override
  public void onDialogPositiveClick(OneInputDialog dialog, String uri) {
    setNodeURI(uri);
  }

  private void setNodeURI(String uri) {
    if (!Validators.HOST_REGEX.matcher(uri).matches()) {
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
    Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show();
    goToHome();
  }

  private void goToHome() {
    Intent intent = new Intent(this, HomeActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    intent.putExtra(HomeActivity.EXTRA_PAGE, 2);
    startActivity(intent);
  }

  public void confirmOpenChannel(View view) {
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
          eclairHelper.openChannel(30, onComplete, pubkey, address, new Switchboard.NewChannel(fundingSat, new MilliSatoshi(0), scala.Option.apply(null)));
        }
      });
    goToHome();
  }
}
