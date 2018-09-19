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

package fr.acinq.eclair.wallet.actors;

import org.greenrobot.eventbus.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import akka.actor.UntypedActor;
import akka.japi.Procedure;
import fr.acinq.eclair.wallet.events.BalanceUpdateEvent;
import fr.acinq.eclair.wallet.utils.Constants;
import scala.concurrent.duration.Duration;

public class BalanceRefreshScheduler extends UntypedActor {
  private final Logger log = LoggerFactory.getLogger(BalanceRefreshScheduler.class);

  public void onReceive(Object message) {
    if (message.equals(Constants.REFRESH)) {
      context().system().scheduler().scheduleOnce(Duration.create(1, TimeUnit.SECONDS), self(), Constants.WAKE_UP, context().dispatcher(), self());
      getContext().become(sleep);
    }
  }

  private Procedure<Object> sleep = message -> {
    if (message.equals(Constants.WAKE_UP)) {
      EventBus.getDefault().post(new BalanceUpdateEvent());
      getContext().unbecome();
    }
  };
}
