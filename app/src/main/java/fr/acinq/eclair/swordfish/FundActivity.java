package fr.acinq.eclair.swordfish;

import android.content.Intent;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ThreadLocalRandom;

import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.package$;

public class FundActivity extends FragmentActivity implements OneInputDialog.OneInputDialogListener {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_fund);
  }

  public void fund_pickRandomNode(View view) {
    String[] uris = new String[3];
    uris[0] = "03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134@54.195.170.196:9735";
    uris[1] = "02225e5da923cc597511bbb21ac88b36a9d13a4fc9fd26089b0323f94de0bc718b@54.91.112.205:9735";
    uris[2] = "02da5cf3b8af5636f4473cbae9ce2d8cc37eaa94afbbb4802af669eadda2ce79bf@54.94.199.157:9735";
    setNodeURI(uris[ThreadLocalRandom.current().nextInt(0, 3)]);
  }

  public void fund_showManualChannelDialog(View view) {
    OneInputDialog dialog = new OneInputDialog();
    dialog.show(getFragmentManager(), "ChannelURIDialog");
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

          TextView openChannelButton = (TextView) findViewById(R.id.fund__button_openchannel);
          openChannelButton.setVisibility(View.VISIBLE);
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

  public void fund_openChannel(View view) {

    TextView amountEV = (TextView) findViewById(R.id.fund__input_amount);
    TextView pubkeyTV = (TextView) findViewById(R.id.fund__value_uri_pubkey);
    TextView ipTV = (TextView) findViewById(R.id.fund__value_uri_ip);
    TextView portTV = (TextView) findViewById(R.id.fund__value_uri_port);

    BinaryData bd = BinaryData.apply(pubkeyTV.getText().toString());
    Crypto.Point point = new Crypto.Point(Crypto.curve().getCurve().decodePoint(package$.MODULE$.binaryData2array(bd)));
    Crypto.PublicKey pk = new Crypto.PublicKey(point, true);
/*
    Switchboard.NewChannel ch = new Switchboard.NewChannel(Long.parseLong(amountEV.getText().toString()), 0L);
    ActorRef sw = EclairHelper.getInstance(this).getSetup().switchboard();
    sw.tell(new Switchboard.NewConnection(pk, new InetSocketAddress(ipTV.getText().toString(), Integer.parseInt(portTV.getText().toString())), Option.apply(ch)), sw);
*/
    Intent intent = new Intent(this, ChannelActivity.class);
    Toast.makeText(this, "Opened channel with " + pk.toString(), Toast.LENGTH_LONG).show();
    startActivity(intent);
  }
}
