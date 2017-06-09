package fr.acinq.eclair.swordfish;

import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.channel.ChannelCreated;
import fr.acinq.eclair.channel.ChannelRestored;
import fr.acinq.eclair.channel.ChannelSignatureReceived;
import fr.acinq.eclair.channel.ChannelStateChanged;
import fr.acinq.eclair.channel.State;
import fr.acinq.eclair.payment.PaymentSent;
import fr.acinq.eclair.swordfish.model.Payment;

public class EclairEventService extends UntypedActor {

  private static final String TAG = "EclairEventService";
  private static Map<ActorRef, ChannelDetails> channelDetailsMap = new ConcurrentHashMap<>();

  public static Map<ActorRef, ChannelDetails> getChannelsMap() {
    return channelDetailsMap;
  }

  public static Satoshi getTotalBalance() {
    Satoshi total = new Satoshi(0);
    for (ChannelDetails d : channelDetailsMap.values()) {
      total = total.$plus(d.balance);
    }
    return total;
  }

  public static Satoshi getBalanceOf(String channelId) {
    return channelDetailsMap.get(channelId).balance;
  }

  public static Satoshi getCapacityOf(String channelId) {
    return channelDetailsMap.get(channelId).capacity;
  }

  private static ChannelDetails getChannelDetails(ActorRef ref) {
    return channelDetailsMap.containsKey(ref) ? channelDetailsMap.get(ref) : new ChannelDetails();
  }

  @Override
  public void onReceive(final Object message) {
    Log.e(TAG, "######## Event: " + message);

    // ---- events that update balance
    if (message instanceof ChannelCreated) {
      ChannelCreated cr = (ChannelCreated) message;
      ChannelDetails cd = getChannelDetails(cr.channel());
      cd.channelId = cr.temporaryChannelId();
      cd.remoteNodeId = cr.remoteNodeId().toString();
      channelDetailsMap.put(((ChannelSignatureReceived) message).channel(), cd);
      EventBus.getDefault().post(new ChannelUpdateEvent());
    }
    else if (message instanceof ChannelSignatureReceived) {
      ChannelSignatureReceived csr = (ChannelSignatureReceived) message;
      ChannelDetails cd = getChannelDetails(csr.channel());
      cd.channelId = csr.Commitments().channelId();
      cd.balance = package$.MODULE$.millisatoshi2satoshi(new MilliSatoshi(csr.Commitments().localCommit().spec().toLocalMsat()));;
      cd.capacity = package$.MODULE$.millisatoshi2satoshi(new MilliSatoshi(csr.Commitments().localCommit().spec().totalFunds()));;
      channelDetailsMap.put(csr.channel(), cd);
      EventBus.getDefault().post(new ChannelUpdateEvent());
    } else if (message instanceof ChannelRestored) {
      ChannelRestored cr = (ChannelRestored) message;
      ChannelDetails cd = getChannelDetails(cr.channel());
      cd.channelId = cr.channelId();
      cd.balance = package$.MODULE$.millisatoshi2satoshi(new MilliSatoshi(cr.currentData().commitments().localCommit().spec().toLocalMsat()));
      cd.capacity = package$.MODULE$.millisatoshi2satoshi(new MilliSatoshi(cr.currentData().commitments().localCommit().spec().totalFunds()));
      cd.remoteNodeId = cr.remoteNodeId().toString();
      channelDetailsMap.put(cr.channel(), cd);
      EventBus.getDefault().post(new ChannelUpdateEvent());
    }
    // ---- channel has been terminated
    else if (message instanceof Terminated) {
      channelDetailsMap.remove(((Terminated) message).getActor());
      EventBus.getDefault().post(new ChannelUpdateEvent());
    }
    // ---- channel state changed
    else if (message instanceof ChannelStateChanged) {
      ChannelStateChanged cs = (ChannelStateChanged) message;
      ChannelDetails cd = getChannelDetails(cs.channel());
      cd.state = cs.currentState();
      Log.i(TAG, "Channel " + cd.channelId + " changed to " + cs.currentState());
      channelDetailsMap.put(cs.channel(), cd);
      EventBus.getDefault().post(new ChannelUpdateEvent());
    }
    // ---- events that update payments status
    else if (message instanceof PaymentSent) {
//      PaymentSent paymentEvent = (PaymentSent) message;
//      List<Payment> paymentList = Payment.findWithQuery(Payment.class, "SELECT * FROM Payment WHERE payment_hash = ? LIMIT 1", paymentEvent.paymentHash().toString());
//      if (paymentList.isEmpty()) {
//        Log.e(TAG, "Received an unknown PaymentSent event. Ignoring");
//      } else {
//        Payment paymentInDB = paymentList.get(0);
//        paymentInDB.amountPaid = Long.toString(paymentEvent.amount().amount());
//        paymentInDB.feesPaid = Long.toString(paymentEvent.feesPaid().amount());
//        paymentInDB.updated = new Date();
//        paymentInDB.status = "PAID";
//        paymentInDB.save();
//      }
    }
  }

  public static class ChannelDetails {
    public Satoshi balance = new Satoshi(0);
    public Satoshi capacity = new Satoshi(0);
    public BinaryData channelId;
    public State state;
    public String remoteNodeId;
  }
}
