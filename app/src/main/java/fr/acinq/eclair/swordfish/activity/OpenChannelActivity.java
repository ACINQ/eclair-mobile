package fr.acinq.eclair.swordfish.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.net.InetSocketAddress;

import akka.actor.ActorRef;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.io.Switchboard;
import fr.acinq.eclair.swordfish.EclairHelper;
import fr.acinq.eclair.swordfish.R;
import fr.acinq.eclair.swordfish.fragment.OneInputDialog;
import fr.acinq.eclair.swordfish.utils.Validators;
import scala.Option;

public class OpenChannelActivity extends Activity implements OneInputDialog.OneInputDialogListener {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_open_channel);

    Intent intent = getIntent();
    String hostURI = intent.getStringExtra(ChannelsListActivity.EXTRA_NEWHOSTURI);
    setNodeURI(hostURI);

    final EditText amountET = (EditText) findViewById(R.id.fund__input_amount);
    amountET.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (s.length() > 0) {
          try {
            Long parsedAmountSat = Long.parseLong(s.toString()) * 100000;
            if (parsedAmountSat < Validators.MIN_FUNDING_SAT || parsedAmountSat >= Validators.MAX_FUNDING_SAT) {
              amountET.setBackgroundColor(getResources().getColor(R.color.lightred));
            } else {
              amountET.setBackgroundColor(Color.TRANSPARENT);
            }
          } catch (NumberFormatException e) {
            goToChannelsList();
          }
        }
      }

      @Override
      public void afterTextChanged(Editable s) {
      }
    });
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
    if (!"".equals(uri)) {
      String[] uriArray = uri.split("@", 2);
      if (uriArray.length == 2) {
        String pubkey = uriArray[0];
        String host = uriArray[1];
        String[] hostArray = host.split(":", 2);
        if (hostArray.length == 2) {
          String ip = hostArray[0];
          String port = hostArray[1];
          findViewById(R.id.fund__button_openchannel).setVisibility(View.VISIBLE);
          TextView pubkeyTV = (TextView) findViewById(R.id.fund__value_uri_pubkey);
          TextView ipTV = (TextView) findViewById(R.id.fund__value_uri_ip);
          TextView portTV = (TextView) findViewById(R.id.fund__value_uri_port);
          pubkeyTV.setText(pubkey);
          ipTV.setText(ip);
          portTV.setText(port);
        }
      }
    }
  }

  public void cancelOpenChannel(View view) {
    Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show();
    goToChannelsList();
  }

  private void goToChannelsList() {
    Intent intent = new Intent(this, ChannelsListActivity.class);
    startActivity(intent);
  }

  public void confirmOpenChannel(View view) {
    EditText amountEV = (EditText) findViewById(R.id.fund__input_amount);
    TextView pubkeyTV = (TextView) findViewById(R.id.fund__value_uri_pubkey);
    TextView ipTV = (TextView) findViewById(R.id.fund__value_uri_ip);
    TextView portTV = (TextView) findViewById(R.id.fund__value_uri_port);

    BinaryData bd = BinaryData.apply(pubkeyTV.getText().toString());
    Crypto.Point point = new Crypto.Point(Crypto.curve().getCurve().decodePoint(package$.MODULE$.binaryData2array(bd)));
    Crypto.PublicKey pk = new Crypto.PublicKey(point, true);

    Switchboard.NewChannel ch = new Switchboard.NewChannel(Long.parseLong(amountEV.getText().toString()), 0L);
    ActorRef sw = EclairHelper.getInstance(getFilesDir()).getSetup().switchboard();
    sw.tell(new Switchboard.NewConnection(pk, new InetSocketAddress(ipTV.getText().toString(), Integer.parseInt(portTV.getText().toString())), Option.apply(ch)), sw);

    Toast.makeText(this, "Opened channel with " + pk.toString(), Toast.LENGTH_LONG).show();
    goToChannelsList();
  }
}
