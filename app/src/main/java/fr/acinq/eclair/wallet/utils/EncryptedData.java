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

import com.tozny.crypto.android.AesCbcWithIntegrity;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.package$;

public abstract class EncryptedData {

  final int version;
  final byte[] salt;
  final AesCbcWithIntegrity.CipherTextIvMac civ;

  EncryptedData(int version, byte[] salt, AesCbcWithIntegrity.CipherTextIvMac civ) {
    this.version = version;
    this.salt = salt;
    this.civ = civ;
  }

  public byte[] write() throws IOException {
    throw new UnsupportedOperationException();
  }

  /**
   * Decrypt an encrypted data object with a password and returns a byte array
   *
   * @param password password protecting the data
   * @return a byte array containing the decrypted data
   * @throws GeneralSecurityException if the password is not correct
   */
  public byte[] decrypt(final String password) throws GeneralSecurityException {
    final AesCbcWithIntegrity.SecretKeys sk = AesCbcWithIntegrity.generateKeyFromPassword(password, salt);
    return AesCbcWithIntegrity.decrypt(civ, sk);
  }

  public static AesCbcWithIntegrity.SecretKeys secretKeyFromBinaryKey(final BinaryData key) {
    final byte[] keyBytes = package$.MODULE$.binaryData2array(key);
    final byte[] confidentialityKeyBytes = new byte[16];
    System.arraycopy(keyBytes, 0, confidentialityKeyBytes, 0, 16);
    final byte[] integrityKeyBytes = new byte[16];
    System.arraycopy(keyBytes, 16, integrityKeyBytes, 0, 16);

    final SecretKey confidentialityKey = new SecretKeySpec(confidentialityKeyBytes, "AES");
    final SecretKey integrityKey = new SecretKeySpec(integrityKeyBytes, "PBKDF2WithHmacSHA1");
    return new AesCbcWithIntegrity.SecretKeys(confidentialityKey, integrityKey);
  }

  public byte[] decrypt(final AesCbcWithIntegrity.SecretKeys key) throws GeneralSecurityException {
    return AesCbcWithIntegrity.decrypt(civ, key);
  }

  public int getVersion() {
    return this.version;
  }
}
