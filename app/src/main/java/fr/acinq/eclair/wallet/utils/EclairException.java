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

package fr.acinq.eclair.wallet.utils;

import fr.acinq.eclair.wallet.models.BackupTypes;
import org.bitcoinj.uri.BitcoinURI;

public interface EclairException {
  class NetworkException extends RuntimeException {
  }

  /**
   * <p>Exception to provide the following to {@link BitcoinURI}:</p>
   * <ul>
   * <li>Provision of parsing error messages</li>
   * </ul>
   * <p>This base exception acts as a general failure mode not attributable to a specific cause (other than
   * that reported in the exception message). Since this is in English, it may not be worth reporting directly
   * to the user other than as part of a "general failure to parse" response.</p>
   */
  class BitcoinURIParseException extends Exception {
    public BitcoinURIParseException(String s) {
      super(s);
    }

    public BitcoinURIParseException(String s, Throwable throwable) {
      super(s, throwable);
    }
  }

  class ExternalStorageUnavailableException extends Exception {
  }

  class UnreadableBackupException extends RuntimeException {
    public final BackupTypes type;

    public UnreadableBackupException(final BackupTypes type, final String message) {
      super(message);
      this.type = type;
    }
  }

}
