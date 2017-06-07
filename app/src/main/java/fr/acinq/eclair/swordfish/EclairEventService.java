package fr.acinq.eclair.swordfish;

import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import akka.actor.UntypedActor;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.bitcoin.package$;
import fr.acinq.eclair.channel.ChannelRestored;
import fr.acinq.eclair.channel.ChannelSignatureReceived;
import fr.acinq.eclair.channel.Commitments;
import fr.acinq.eclair.payment.PaymentSent;
import fr.acinq.eclair.swordfish.model.Payment;

public class EclairEventService extends UntypedActor {

  private static final String TAG = "EclairEventService";
  private static Map<String, ChannelDetails> channelDetailsMap = new ConcurrentHashMap<>();

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

  @Override
  public void onReceive(final Object message) {
    Log.e(TAG, "##################################### Got event in thread: " + Thread.currentThread().getName());
    Log.e(TAG, "Event: " + message);

    // ---- events that update balance
    if (message instanceof ChannelSignatureReceived) {
      Commitments c = ((ChannelSignatureReceived) message).Commitments();
      Satoshi balance = package$.MODULE$.millisatoshi2satoshi(new MilliSatoshi(c.localCommit().spec().toLocalMsat()));
      Satoshi capacity = package$.MODULE$.millisatoshi2satoshi(new MilliSatoshi(c.localCommit().spec().totalFunds()));
      channelDetailsMap.put(c.channelId().toString(), new ChannelDetails(balance, capacity));
      EventBus.getDefault().post(new BalanceEvent(c.channelId().toString(), balance));
    } else if (message instanceof ChannelRestored) {
      Commitments c = ((ChannelRestored) message).currentData().commitments();
      Satoshi balance = package$.MODULE$.millisatoshi2satoshi(new MilliSatoshi(c.localCommit().spec().toLocalMsat()));
      Satoshi capacity = package$.MODULE$.millisatoshi2satoshi(new MilliSatoshi(c.localCommit().spec().totalFunds()));
      channelDetailsMap.put(c.channelId().toString(), new ChannelDetails(balance, capacity));
      EventBus.getDefault().post(new BalanceEvent(c.channelId().toString(), balance));
    }
    // ---- events that update payments status
    if (message instanceof PaymentSent) {
      PaymentSent paymentEvent = (PaymentSent) message;
      List<Payment> paymentList = Payment.findWithQuery(Payment.class, "SELECT * FROM Payment WHERE paymentHash = ? LIMIT 1", paymentEvent.paymentHash().toString());
      if (paymentList.isEmpty()) {
        Log.e(TAG, "Received an unknown PaymentSent event. Ignoring");
      } else {
        Payment paymentInDB = paymentList.get(0);
        paymentInDB.amountPaid = Long.toString(paymentEvent.amount().amount());
        paymentInDB.feesPaid = Long.toString(paymentEvent.feesPaid().amount());
        paymentInDB.updated = new Date();
        paymentInDB.status = "PAID";
        paymentInDB.save();
      }
    }
  }

  static class ChannelDetails {
    Satoshi balance;
    Satoshi capacity;

    ChannelDetails(Satoshi balance, Satoshi capacity) {
      this.balance = balance;
      this.capacity = capacity;
    }
  }
}
