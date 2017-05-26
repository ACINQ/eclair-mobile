/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.eclair.swordfish;

public final class Constants {

  public static final String BROADCAST_ACTION = "fr.acinq.eclair.swordfish.BROADCAST";
  public static final String EXTENDED_DATA_STATUS = "fr.acinq.eclair.swordfish.STATUS";

  public static final int STATE_PAYMENT_STARTED = 0;
  public static final int STATE_PAYMENT_SENT = 1;
  public static final int STATE_PAYMENT_PREIMAGE = 2;
  public static final int STATE_PAYMENT_COMPLETE = 3;
  public static final int STATE_PAYMENT_FAILED = -1;
}
