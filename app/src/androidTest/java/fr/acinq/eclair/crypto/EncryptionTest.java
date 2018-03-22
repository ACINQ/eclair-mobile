package fr.acinq.eclair.crypto;

import com.tozny.crypto.android.AesCbcWithIntegrity;

import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Created by fabrice on 22/03/18.
 */

public class EncryptionTest {
  @Test
  public void basicTest() throws GeneralSecurityException, UnsupportedEncodingException {

    String password = "foobar";
    byte[] salt = AesCbcWithIntegrity.generateSalt();
    AesCbcWithIntegrity.SecretKeys keys = AesCbcWithIntegrity.generateKeyFromPassword(password, salt);
    byte[] plaintext = "this is not encrypted".getBytes("UTF-8");
    AesCbcWithIntegrity.CipherTextIvMac civ = AesCbcWithIntegrity.encrypt(plaintext, keys);
    byte[] check = AesCbcWithIntegrity.decrypt(civ, keys);
    assert(Arrays.equals(plaintext, check));
  }
}

