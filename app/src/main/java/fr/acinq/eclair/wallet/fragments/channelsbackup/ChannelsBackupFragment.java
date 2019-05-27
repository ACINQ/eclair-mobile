/*
 * Copyright 2019 ACINQ SAS
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

package fr.acinq.eclair.wallet.fragments.channelsbackup;

import androidx.fragment.app.Fragment;

/**
 * This fragment help managing the current settings of the "lightning channels backup" feature.
 *
 * Mission statement:
 * - tell the user what settings are currently used, eg no backup, on-device, google drive...
 * - let the user change said settings.
 * - give the user information about the backup files.
 *
 * This fragment is used by a setup window when starting the app for the first time, and by the channels backup setting page.
 */
public class ChannelsBackupFragment extends Fragment {
  
}
