/*
 * Copyright 2018 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.eclair.wallet.models;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

import fr.acinq.eclair.channel.ChannelException;
import fr.acinq.eclair.payment.PaymentLifecycle;
import fr.acinq.eclair.router.Hop;
import fr.acinq.eclair.router.RouteNotFound$;
import scala.collection.JavaConverters;

/**
 * Wraps information about a failed lightning payment returned by eclair-core. Implements Parcelable
 * so that it can be passed between activities with intents.
 * <ul>
 * <li>The <b>type</b> field tells if this error is a remote or a local failure.
 * <li>The <b>cause</b> field contains a message detailing the reason of this failure.
 * <li>The <b>origin</b> field should be the id of the node from which this error comes from. May be null (for local failure).
 * <li>The <b>originChannelId</b> is the id of the channel which rejected the payment. May be null.
 * <li>The <b>hops</b> field is set only for the remote failures and describes the route used by the failed payment.
 */
public class LightningPaymentError implements Parcelable {

  public static final Creator<LightningPaymentError> CREATOR = new Creator<LightningPaymentError>() {
    @Override
    public LightningPaymentError createFromParcel(Parcel in) {
      return new LightningPaymentError(in);
    }

    @Override
    public LightningPaymentError[] newArray(int size) {
      return new LightningPaymentError[size];
    }
  };

  private String type;
  private String cause;
  private String origin;
  private String originChannelId;
  private List<String> hops;

  public LightningPaymentError(String type, String cause, String origin, String originChannelId, List<String> hops) {
    this.type = type;
    this.cause = cause;
    this.origin = origin;
    this.originChannelId = originChannelId;
    this.hops = hops == null ? new ArrayList<String>() : hops;
  }

  public LightningPaymentError(Parcel in) {
    type = in.readString();
    cause = in.readString();
    origin = in.readString();
    originChannelId = in.readString();
    List<String> h = new ArrayList<>();
    in.readList(h, List.class.getClassLoader());
  }

  /**
   * Parses a {@link PaymentLifecycle.PaymentFailure} sent by eclair core and generates a {@link LightningPaymentError}.
   * According to the failure type, the resulting error may contain a list of the nodes in the failed route.
   * The type of the error is always set, as well as the cause, be it unknown.
   *
   * @param failure failure in the payment route
   * @return
   */
  public static LightningPaymentError generateDetailedErrorCause(final PaymentLifecycle.PaymentFailure failure) {
    if (failure instanceof PaymentLifecycle.RemoteFailure) {
      final PaymentLifecycle.RemoteFailure rf = (PaymentLifecycle.RemoteFailure) failure;
      final String type = rf.getClass().getSimpleName();
      final String cause = rf.e().failureMessage().message();
      final String origin = rf.e().originNode().toString();
      String originChannelId = null;
      final List<String> hopsNodesPK = new ArrayList<>();
      if (rf.route().size() > 0) {
        final List<Hop> hops = JavaConverters.seqAsJavaListConverter(rf.route()).asJava();
        for (int hi = 0; hi < hops.size(); hi++) {
          Hop h = hops.get(hi);
          if (hi == 0) {
            hopsNodesPK.add(h.nodeId().toString());
          }
          if (origin.equals(h.nodeId().toString())) {
            originChannelId = h.lastUpdate().shortChannelId().toString();
          }
          hopsNodesPK.add(h.nextNodeId().toString());
        }
      }
      return new LightningPaymentError(type, cause, origin, originChannelId, hopsNodesPK);

    } else if (failure instanceof PaymentLifecycle.LocalFailure) {
      final PaymentLifecycle.LocalFailure lf = (PaymentLifecycle.LocalFailure) failure;
      final String type = lf.getClass().getSimpleName();
      String cause;
      String originChannelId = null;
      Throwable t = lf.t();
      if (t instanceof RouteNotFound$) {
        cause = "The wallet could not find a path to the payee.";
      } else if (t instanceof PaymentLifecycle.RouteTooExpensive) {
        cause = "Routing fees exceed 3% and are deemed too expensive. This check can be disabled in the application\'s preferences.";
      } else if (t instanceof ChannelException){
        cause = t.getMessage();
        originChannelId = ((ChannelException) t).channelId().toString();
      } else {
        cause = t.getClass().getSimpleName();
      }
      return new LightningPaymentError(type, cause, null, originChannelId, null);

    } else if (failure instanceof PaymentLifecycle.UnreadableRemoteFailure) {
      final PaymentLifecycle.UnreadableRemoteFailure unreadable = (PaymentLifecycle.UnreadableRemoteFailure) failure;
      final String type = unreadable.getClass().getSimpleName();
      final String cause = "A peer on the route failed the payment with an non readable cause";
      final List<String> hopsNodesPK = new ArrayList<>();
      if (unreadable.route().size() > 0) {
        final List<Hop> hops = JavaConverters.seqAsJavaListConverter(unreadable.route()).asJava();
        for (int hi = 0; hi < hops.size(); hi++) {
          Hop h = hops.get(hi);
          if (hi == 0) {
            hopsNodesPK.add(h.nodeId().toString());
          }
          hopsNodesPK.add(h.nextNodeId().toString());
        }
      }
      return new LightningPaymentError(type, cause, null, null, hopsNodesPK);

    } else {
      return new LightningPaymentError("Unknown Error:" + failure.getClass().getSimpleName(), "Unknown Cause", null, null, null);
    }
  }

  public String getType() {
    return type;
  }

  public String getCause() {
    return cause;
  }

  public String getOrigin() {
    return origin;
  }

  public String getOriginChannelId() {
    return originChannelId;
  }

  public List<String> getHops() {
    return hops;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel parcel, int flags) {
    parcel.writeString(type);
    parcel.writeString(cause);
    parcel.writeString(origin);
    parcel.writeString(originChannelId);
    parcel.writeList(hops);
  }
}
