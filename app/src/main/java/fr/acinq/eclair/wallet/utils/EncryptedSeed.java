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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class EncryptedSeed {

  public final static byte SEED_FILE_VERSION_1 = 1;
  private static final int SALT_LENGTH_V1 = 128;
  private static final int IV_LENGTH_V1 = 16;
  private static final int MAC_LENGTH_V1 = 32;
  private final int version;
  private final byte[] salt;
  private final AesCbcWithIntegrity.CipherTextIvMac civ;

  private EncryptedSeed(int version, byte[] salt, AesCbcWithIntegrity.CipherTextIvMac civ) {
    this.version = version;
    this.salt = salt;
    this.civ = civ;
  }

  /**
   * Encrypt a non encrypted seed with AES CBC and return an object containing the encrypted seed.
   *
   * @param seed     the seed to encrypt
   * @param password the password encrypting the seed
   * @param version  the version describing the serialization to use for the EncryptedSeed object
   * @return a encrypted seed ready to be serialized
   * @throws GeneralSecurityException
   */
  static EncryptedSeed encrypt(final byte[] seed, final String password, final int version) throws GeneralSecurityException {
    final byte[] salt = AesCbcWithIntegrity.generateSalt();
    final AesCbcWithIntegrity.SecretKeys sk = AesCbcWithIntegrity.generateKeyFromPassword(password, salt);
    final AesCbcWithIntegrity.CipherTextIvMac civ = AesCbcWithIntegrity.encrypt(seed, sk);
    return new EncryptedSeed(version, salt, civ);
  }

  /**
   * Read an array of byte and deserializes it as an EncryptedSeed object.
   *
   * @param serialized array to deserialize
   * @return
   */
  public static EncryptedSeed read(final byte[] serialized) {
    final ByteArrayInputStream stream = new ByteArrayInputStream(serialized);
    final int version = stream.read();
    if (version == SEED_FILE_VERSION_1) {
      final byte[] salt = new byte[SALT_LENGTH_V1];
      stream.read(salt, 0, SALT_LENGTH_V1);
      final byte[] iv = new byte[IV_LENGTH_V1];
      stream.read(iv, 0, IV_LENGTH_V1);
      final byte[] mac = new byte[MAC_LENGTH_V1];
      stream.read(mac, 0, MAC_LENGTH_V1);
      final byte[] cipher = new byte[stream.available()];
      stream.read(cipher, 0, stream.available());
      return new EncryptedSeed(version, salt, new AesCbcWithIntegrity.CipherTextIvMac(cipher, iv, mac));
    } else {
      throw new RuntimeException("unhandled encrypted seed file version");
    }
  }

  /**
   * Serializes an encrypted seed as a byte array.
   */
  public byte[] write() throws IOException {
    if (version == SEED_FILE_VERSION_1) {
      if (salt.length != SALT_LENGTH_V1 || civ.getIv().length != IV_LENGTH_V1 || civ.getMac().length != MAC_LENGTH_V1) {
        throw new RuntimeException("could not serialize seed because fields are not of the right length");
      }
      final ByteArrayOutputStream array = new ByteArrayOutputStream();
      array.write(version);
      array.write(salt);
      array.write(civ.getIv());
      array.write(civ.getMac());
      array.write(civ.getCipherText());
      return array.toByteArray();
    } else {
      throw new RuntimeException("unhandled version");
    }
  }

  /**
   * Decrypt an encrypted seed with a password and returns a byte array
   *
   * @param password password protecting the seed
   * @return a byte array containing the decrypted seed
   * @throws GeneralSecurityException if the password is not correct
   */
  byte[] decrypt(final String password) throws GeneralSecurityException {
    final AesCbcWithIntegrity.SecretKeys sk = AesCbcWithIntegrity.generateKeyFromPassword(password, salt);
    return AesCbcWithIntegrity.decrypt(civ, sk);
  }

}
