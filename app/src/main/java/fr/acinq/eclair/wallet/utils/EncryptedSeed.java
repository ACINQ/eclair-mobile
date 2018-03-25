package fr.acinq.eclair.wallet.utils;

import com.tozny.crypto.android.AesCbcWithIntegrity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class EncryptedSeed {

  private static final int VERSION_LENGTH = 1;
  private static final int SALT_LENGTH_V1 = 128;
  private static final int IV_LENGTH_V1 = 16;
  private static final int MAC_LENGTH_V1 = 32;
  public final static byte SEED_FILE_VERSION_1 = 1;

  private final int version;
  private final byte[] salt;
  private final AesCbcWithIntegrity.CipherTextIvMac civ;

  private EncryptedSeed(int version, byte[] salt, AesCbcWithIntegrity.CipherTextIvMac civ) {
    this.version = version;
    this.salt = salt;
    this.civ = civ;
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

  /**
   * Encrypt a non encrypted seed with AES CBC and return an object containing the encrypted seed.
   * @param seed the seed to encrypt
   * @param password the password encrypting the seed
   * @param version the version describing the serialization to use for the EncryptedSeed object
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
   * @param serialized array to deserialize
   * @return
   */
  public static EncryptedSeed read(final byte[] serialized) {
    final byte version = serialized[0];
    if (version == SEED_FILE_VERSION_1) {
      final byte[] salt = readSalt_v1(serialized);
      final AesCbcWithIntegrity.CipherTextIvMac civ = new AesCbcWithIntegrity.CipherTextIvMac(
        readCipher_v1(serialized), readIV_v1(serialized), readMAC_v1(serialized));
      return new EncryptedSeed(version, salt, civ);
    } else {
      throw new RuntimeException("unhandled encrypted seed file version");
    }
  }

  /* --- utility methods for seed serialization version 1 --- */

  private static byte[] readSalt_v1(byte[] content) {
    final byte[] salt = new byte[SALT_LENGTH_V1];
    System.arraycopy(content, VERSION_LENGTH, salt, 0, SALT_LENGTH_V1);
    return salt;
  }

  private static byte[] readIV_v1(byte[] content) {
    final byte[] iv = new byte[IV_LENGTH_V1];
    System.arraycopy(content, VERSION_LENGTH + SALT_LENGTH_V1, iv, 0, IV_LENGTH_V1);
    return iv;
  }

  private static byte[] readMAC_v1(byte[] content) {
    final byte[] mac = new byte[MAC_LENGTH_V1];
    System.arraycopy(content, VERSION_LENGTH + SALT_LENGTH_V1 + IV_LENGTH_V1, mac, 0, MAC_LENGTH_V1);
    return mac;
  }

  private static byte[] readCipher_v1(byte[] content) {
    final int cipherLength = content.length - VERSION_LENGTH - SALT_LENGTH_V1 - IV_LENGTH_V1 - MAC_LENGTH_V1;
    final byte[] cipher = new byte[cipherLength];
    System.arraycopy(content, VERSION_LENGTH + SALT_LENGTH_V1 + IV_LENGTH_V1 + MAC_LENGTH_V1, cipher, 0, cipherLength);
    return cipher;
  }

}
