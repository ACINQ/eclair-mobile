package fr.acinq.eclair.swordfish;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;

import akka.actor.ActorRef;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.Crypto;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.NodeParams;
import fr.acinq.eclair.Setup;
import fr.acinq.eclair.io.PeerRecord;
import fr.acinq.eclair.io.Switchboard;
import scala.Option;

public class LauncherActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_launcher);

    File data = new File(getApplicationContext().getFilesDir(), "eclair-wallet-data");
    Log.i("launcher", "Data dir exists ? " + (new File(data, "seed.dat")).exists());
    for (String f : (new File(data, "db")).list()) {
      Log.i("launcher", "File in db dir : " + f);
    }
    Setup s = new Setup(data, "system");
    s.boostrap();

    // 02da5cf3b8af5636f4473cbae9ce2d8cc37eaa94afbbb4802af669eadda2ce79bf@54.94.199.157:9735

    BinaryData bd = BinaryData.apply("02da5cf3b8af5636f4473cbae9ce2d8cc37eaa94afbbb4802af669eadda2ce79bf");
    Crypto.Point point = new Crypto.Point(Crypto.curve().getCurve().decodePoint(package$.MODULE$.binaryData2array(bd)));
    //Crypto.PublicKey pk = Crypto.PublicKey.apply(bd);
    Crypto.PublicKey pk = new Crypto.PublicKey(point, true);

    s.nodeParams().peersDb().put(pk, PeerRecord.apply(pk, new InetSocketAddress("54.94.199.157", 9735)));
    Log.i("fr.acinq.eclair", "ok");

    Switchboard.NewChannel ch = new Switchboard.NewChannel(100000L, 0L);
    ActorRef sw = s.switchboard();
    sw.tell(new Switchboard.NewConnection(pk, new InetSocketAddress("54.94.199.157", 9735), Option.apply(ch)), sw);
  }
  public void launcher_readFile(View view) {
    String content = "bloup";
    try {
      FileInputStream fis = getApplicationContext().openFileInput("eclair-wallet-data/seed.dat");
      InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
      BufferedReader bufferedReader = new BufferedReader(isr);
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = bufferedReader.readLine()) != null) {
        sb.append(line).append("\n");
      }
      content = sb.toString();
    } catch (FileNotFoundException e) {
      Log.e("File", "file not found ", e);
    } catch (UnsupportedEncodingException e) {
      Log.e("File", "Unsupported encoding ", e);
    } catch (IOException e) {
      Log.e("File", "IO Error", e);
    }
    TextView t = (TextView) findViewById(R.id.filecontent);
    t.setText(content);
  }

  public void launcher_goToChannel(View view) {
    Intent intent = new Intent(this, ChannelActivity.class);
    startActivity(intent);
  }
}
