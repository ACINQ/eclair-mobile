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

package fr.acinq.eclair.wallet.utils;

import java.util.regex.Pattern;

/**
 * Created by Dominique on 07/06/2017.
 */

public class Validators {
  public static final long MIN_LEFTOVER_ONCHAIN_BALANCE_SAT = 100000L; // minimal amount that should stay onchain to handle fees
  public static final Pattern HOST_REGEX = Pattern.compile("([a-fA-F0-9]{66})@([a-zA-Z0-9:\\.\\-_]+)(:([0-9]+))?");
}
