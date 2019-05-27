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

package fr.acinq.eclair.crypto;

import com.tozny.crypto.android.AesCbcWithIntegrity;
import fr.acinq.bitcoin.DeterministicWallet;
import fr.acinq.eclair.wallet.utils.EncryptedBackup;
import fr.acinq.eclair.wallet.utils.EncryptedData;
import org.junit.Assert;
import org.junit.Test;
import scodec.bits.ByteVector;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

public class BackupEncryptionTest {

  @Test
  public void encryptWithSeed_v1() throws GeneralSecurityException {

    // create a master key from a random seed
    byte[] seed = new byte[16];
    new SecureRandom().nextBytes(seed);
    final DeterministicWallet.ExtendedPrivateKey xpriv = DeterministicWallet.generate(ByteVector.view(seed));

    // derive a hardened key from xpriv
    // hardened means that, even if the key is compromised, it is not possible to find the parent key
    final AesCbcWithIntegrity.SecretKeys key = EncryptedData.secretKeyFromBinaryKey(EncryptedBackup.generateBackupKey_v1(xpriv));

    // data to encrypt
    byte[] plaintext = new byte[300];
    new SecureRandom().nextBytes(plaintext);

    // apply encryption
    EncryptedBackup encrypted = EncryptedBackup.encrypt(plaintext, key, EncryptedBackup.BACKUP_VERSION_1);
    byte[] decrypted = encrypted.decrypt(key);

    Assert.assertTrue(AesCbcWithIntegrity.constantTimeEq(plaintext, decrypted));
  }

  @Test
  public void encryptWithSeed_v2() throws GeneralSecurityException {

    // create a master key from a random seed
    byte[] seed = new byte[16];
    new SecureRandom().nextBytes(seed);
    final DeterministicWallet.ExtendedPrivateKey xpriv = DeterministicWallet.generate(ByteVector.view(seed));

    // derive a hardened key from xpriv
    // hardened means that, even if the key is compromised, it is not possible to find the parent key
    final AesCbcWithIntegrity.SecretKeys key = EncryptedData.secretKeyFromBinaryKey(EncryptedBackup.generateBackupKey_v2(xpriv));

    // data to encrypt
    byte[] plaintext = new byte[300];
    new SecureRandom().nextBytes(plaintext);

    // apply encryption
    EncryptedBackup encrypted = EncryptedBackup.encrypt(plaintext, key, EncryptedBackup.BACKUP_VERSION_2);
    byte[] decrypted = encrypted.decrypt(key);

    Assert.assertTrue(AesCbcWithIntegrity.constantTimeEq(plaintext, decrypted));
  }

}
