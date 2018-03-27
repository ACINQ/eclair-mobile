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

public class NotificationEvent {
  public final static int NOTIF_CHANNEL_CLOSED_ID = 1;

  public final int id;
  public final String tag;
  public final String title;
  public final String message;
  public final String bigMessage;

  public NotificationEvent(int id, String tag, String title, String message, String bigMessage) {
    this.id = id;
    this.tag = tag;
    this.title = title;
    this.message = message;
    this.bigMessage = bigMessage;
  }
}
