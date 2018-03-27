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

import fr.acinq.bitcoin.MilliSatoshi;

public class ChannelItem {
  public final String id;
  public final MilliSatoshi capacityMsat;
  public final String targetPubkey;
  public String state;
  public Boolean isCooperativeClosing;
  public MilliSatoshi balanceMsat;

  public ChannelItem(String id, MilliSatoshi capacityMsat, String targetPubkey) {
    this.id = id;
    this.capacityMsat = capacityMsat;
    this.targetPubkey = targetPubkey;
  }
}
