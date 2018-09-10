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

import fr.acinq.bitcoin.BinaryData;
import fr.acinq.bitcoin.DeterministicWallet;
import fr.acinq.eclair.wallet.BuildConfig;

public class EncryptedBackup extends EncryptedData {

  /**
   * Version 1 uses the same derivation path as BIP49 for the encryption key.
   *
   * @see #generateBackupKey_v1(DeterministicWallet.ExtendedPrivateKey)
   * @deprecated should only be used to decrypt older files, not to encrypt new files.
   */
  public final static byte BACKUP_VERSION_1 = 1;

  /**
   * Version 2 uses either m/42'/0' (mainnet) or m/42'/1' (testnet) as derivation path for the encryption key.
   * This is the only difference with version 1.
   *
   * @see #generateBackupKey_v2(DeterministicWallet.ExtendedPrivateKey)
   */
  public final static byte BACKUP_VERSION_2 = 2;

  private static final int IV_LENGTH_V1 = 16;
  private static final int MAC_LENGTH_V1 = 32;

  private EncryptedBackup(int version, AesCbcWithIntegrity.CipherTextIvMac civ) {
    super(version, null, civ);
  }

  /**
   * Encrypt data with AES CBC and return an EncryptedBackup object containing the encrypted data.
   *
   * @param data    data to encrypt
   * @param key     the secret key encrypting the data
   * @param version the version describing the serialization to use for the EncryptedBackup object
   * @return a encrypted backup object ready to be serialized
   * @throws GeneralSecurityException
   */
  public static EncryptedBackup encrypt(final byte[] data, final AesCbcWithIntegrity.SecretKeys key, final int version) throws GeneralSecurityException {
    final AesCbcWithIntegrity.CipherTextIvMac civ = AesCbcWithIntegrity.encrypt(data, key);
    return new EncryptedBackup(version, civ);
  }

  /**
   * Read an array of byte and deserializes it as an EncryptedBackup object.
   *
   * @param serialized array to deserialize
   * @return
   */
  public static EncryptedBackup read(final byte[] serialized) {
    final ByteArrayInputStream stream = new ByteArrayInputStream(serialized);
    final int version = stream.read();
    if (version == BACKUP_VERSION_1 || version == BACKUP_VERSION_2) {
      final byte[] iv = new byte[IV_LENGTH_V1];
      stream.read(iv, 0, IV_LENGTH_V1);
      final byte[] mac = new byte[MAC_LENGTH_V1];
      stream.read(mac, 0, MAC_LENGTH_V1);
      final byte[] cipher = new byte[stream.available()];
      stream.read(cipher, 0, stream.available());
      return new EncryptedBackup(version, new AesCbcWithIntegrity.CipherTextIvMac(cipher, iv, mac));
    } else {
      throw new RuntimeException("unhandled encrypted backup version");
    }
  }

  /**
   * Derives a hardened key from the extended key. This is used to encrypt/decrypt the channels backup files.
   * Path is the same as BIP49.
   */
  public static BinaryData generateBackupKey_v1(final DeterministicWallet.ExtendedPrivateKey pk) {
    final DeterministicWallet.ExtendedPrivateKey dpriv = DeterministicWallet.derivePrivateKey(pk,
      DeterministicWallet.KeyPath$.MODULE$.apply("m/49'"));
    return dpriv.secretkeybytes();
  }

  /**
   * Derives a hardened key from the extended key. This is used to encrypt/decrypt the channels backup files.
   * Path depends on the chain used by the wallet, mainnet or testnet.
   */
  public static BinaryData generateBackupKey_v2(final DeterministicWallet.ExtendedPrivateKey pk) {
    final DeterministicWallet.ExtendedPrivateKey dpriv = DeterministicWallet.derivePrivateKey(pk,
      DeterministicWallet.KeyPath$.MODULE$.apply("mainnet".equals(BuildConfig.CHAIN) ? "m/42'/0'" : "m/42'/1'"));
    return dpriv.secretkeybytes();
  }

  /**
   * Serializes an encrypted backup as a byte array, with the result depending on the object version.
   */
  @Override
  public byte[] write() throws IOException {
    if (version == BACKUP_VERSION_1 || version == BACKUP_VERSION_2) {
      if (civ.getIv().length != IV_LENGTH_V1 || civ.getMac().length != MAC_LENGTH_V1) {
        throw new RuntimeException("could not serialize backup because fields are not of the correct length");
      }
      final ByteArrayOutputStream array = new ByteArrayOutputStream();
      array.write(version);
      array.write(civ.getIv());
      array.write(civ.getMac());
      array.write(civ.getCipherText());
      return array.toByteArray();
    } else {
      throw new RuntimeException("unhandled version");
    }
  }
}
