package fr.acinq.eclair.swordfish;

import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import akka.actor.UntypedActor;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.channel.ChannelRestored;
import fr.acinq.eclair.channel.ChannelSignatureReceived;
import fr.acinq.eclair.channel.Commitments;

public class EclairEventService extends UntypedActor {
  private static final String TAG = "EclairEventService";
  private static Map<String, Satoshi> channelBalanceMap = new ConcurrentHashMap<>();

  @Override
  public void onReceive(final Object message) {
    Log.e(TAG, "##################################### Got event in thread: " + Thread.currentThread().getName());
    Log.e(TAG, "Event: " + message);

    if (message instanceof ChannelSignatureReceived) {
      Commitments c = ((ChannelSignatureReceived) message).Commitments();
      Satoshi balance = package$.MODULE$.millisatoshi2satoshi(new MilliSatoshi(c.localCommit().spec().toLocalMsat()));
      channelBalanceMap.put(c.channelId().toString(), balance);
      EventBus.getDefault().post(new BalanceEvent(c.channelId().toString(), balance));
    } else if (message instanceof ChannelRestored) {
      Commitments c = ((ChannelRestored) message).currentData().commitments();
      Satoshi balance = package$.MODULE$.millisatoshi2satoshi(new MilliSatoshi(c.localCommit().spec().toLocalMsat()));
      channelBalanceMap.put(c.channelId().toString(), balance);
      EventBus.getDefault().post(new BalanceEvent(c.channelId().toString(), balance));
    }
  }

  public static Satoshi getTotalBalance() {
    Satoshi total = new Satoshi(0);
    for (Satoshi ms : channelBalanceMap.values()) {
      total = total.$plus(ms);
    }
    return total;
  }
}
