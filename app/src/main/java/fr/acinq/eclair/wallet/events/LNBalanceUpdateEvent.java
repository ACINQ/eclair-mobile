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

import fr.acinq.bitcoin.MilliSatoshi;

/**
 * Contains the balance of the Lightning node channels. Balance amounts are in milli-satoshis.
 *
 * <ul>
 *     <li>available: this balance is spendable.</li>
 *     <li>pending: this balance is NOT spendable but should be soon (waiting for confirmations).</li>
 *     <li>offline: this balance is NOT spendable because on the channels endpoint is offline.</li>
 *     <li>closing: this balance is NOT spendable and will never be because the channel is closing.</li>
 * </ul>
 */
public class LNBalanceUpdateEvent {
  public final long availableBalanceMsat;
  public final long pendingBalanceMsat;
  public final long offlineBalanceMsat;
  public final long closingBalanceMsat;
  public final long ignoredBalanceMsat;

  public LNBalanceUpdateEvent(long availableBalanceMsat, long pendingBalanceMsat, long offlineBalanceMsat, long closingBalanceMsat, long ignoredBalanceMsat) {
    this.availableBalanceMsat = availableBalanceMsat;
    this.pendingBalanceMsat = pendingBalanceMsat;
    this.offlineBalanceMsat = offlineBalanceMsat;
    this.closingBalanceMsat = closingBalanceMsat;
    this.ignoredBalanceMsat = ignoredBalanceMsat;
  }

  /**
   * Calculates the total balance, including the non spendable balances. This total ignores the closing balance.
   * @return
   */
  public MilliSatoshi total() {
    // ignoring closing balance
    return new MilliSatoshi(availableBalanceMsat + pendingBalanceMsat + offlineBalanceMsat);
  }
}
