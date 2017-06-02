package fr.acinq.eclair.swordfish;

import android.content.Context;

import com.orm.SugarApp;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import fr.acinq.eclair.channel.ChannelEvent;

/**
 * Created by Dominique on 22/05/2017.
 */

public class App extends SugarApp {
  @Override
  protected void attachBaseContext(Context base) {
    super.attachBaseContext(base);
  }
}
