package cloud.server;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;

public class PasswordHash {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA1";

    public static ArrayList<byte[]> hash(String pass) throws NoSuchAlgorithmException, InvalidKeySpecException {

        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);

        byte[] hash = getHashPBKDF2(pass, salt);

        ArrayList<byte[]> res = new ArrayList<>();
        res.add(salt);
        res.add(hash);
        return res;
    }

    private static byte[] getHashPBKDF2(String pass, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(pass.toCharArray(), salt, 65536, 128);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
        return factory.generateSecret(spec).getEncoded();
    }

    public static boolean validatePass(String pass, byte[] salt, byte[] trueHash) throws NoSuchAlgorithmException, InvalidKeySpecException {

        byte[] hash = getHashPBKDF2(pass, salt);

        if (hash.length != trueHash.length) {
            return false;
        }

        boolean res = true;
        for (int i = 0; i < hash.length; i++) {
            if (hash[i] != trueHash[i]) {
                res = false;
            }
        }
        return res;
    }

}
