package fr.acinq.eclair.swordfish;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.util.Timeout;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.eclair.channel.CMD_GETINFO$;
import fr.acinq.eclair.channel.RES_GETINFO;
import fr.acinq.eclair.swordfish.adapters.ChannelListItemAdapter;
import fr.acinq.eclair.swordfish.model.ChannelItem;
import scala.Symbol;
import scala.collection.Iterator;
import scala.collection.JavaConversions;
import scala.collection.immutable.Map;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

/**
 * Created by Dominique on 26/05/2017.
 */

public class ChannelsListTask extends AsyncTask<String, Integer,List<ChannelItem>> {

  public interface AsyncChannelsListResponse {
    void processFinish(List<ChannelItem> output);
  }

  private AsyncChannelsListResponse delegate;
  private Context context;

  public ChannelsListTask(AsyncChannelsListResponse delegate, Context context) {
    this.delegate = delegate;
    this.context = context;
  }

  @Override
  protected List<ChannelItem> doInBackground(String... params) {
    ActorRef register = EclairHelper.getInstance(context).getSetup().register();
    Timeout timeout = new Timeout(Duration.create(5, "seconds"));
    Future<Object> future = Patterns.ask(register, new Symbol("channels"), timeout);
    List<ChannelItem> channelsList = new ArrayList();
    try {
      Map<BinaryData, ActorRef> result = (Map<BinaryData, ActorRef>) Await.result(future, timeout.duration());
      java.util.Map<BinaryData, ActorRef> m = JavaConversions.mapAsJavaMap(result);
      for (ActorRef a : m.values()) {
        Future<Object> futureActor = Patterns.ask(a, CMD_GETINFO$.MODULE$, timeout);
        RES_GETINFO detailActor = (RES_GETINFO) Await.result(futureActor, timeout.duration());
        ChannelItem ci = new ChannelItem(detailActor.channelId().toString(), 0L, detailActor.nodeid().toString());
        ci.status = detailActor.state().toString();
        channelsList.add(ci);
      }
    } catch (Exception e) {
      Log.e("Channels list", "Asking the list of channels from register has failed", e);
    }
    return channelsList;
  }

  protected void onPostExecute(List<ChannelItem> result) {
    delegate.processFinish(result);
  }
}
