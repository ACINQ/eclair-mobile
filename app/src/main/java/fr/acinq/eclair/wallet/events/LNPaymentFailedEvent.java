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

package fr.acinq.eclair.wallet.events;

import java.util.ArrayList;
import java.util.List;

import fr.acinq.eclair.wallet.models.LightningPaymentError;

public class LNPaymentFailedEvent {
  public final String paymentHash;
  public final String paymentDescription;
  public final boolean isSimple;
  public final String simpleMessage;
  public final ArrayList<LightningPaymentError> errors;

  public LNPaymentFailedEvent(final String paymentHash, final String paymentDescription, final boolean isSimple, final String simpleMessage, final ArrayList<LightningPaymentError> errors) {
    this.paymentHash = paymentHash;
    this.paymentDescription = paymentDescription;
    this.isSimple = isSimple;
    this.simpleMessage = simpleMessage;
    this.errors = errors;
  }
}
