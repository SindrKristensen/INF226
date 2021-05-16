package inf226.inchat;

import com.lambdaworks.crypto.SCryptUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Password {

    public Password(){ }

    /**
     * Gets inn a string key, and checks that it is correct to the NIST and then crypts it and returns it
     */
    public static String getPassword(String key) {
        int N = 16384;
        int r = 8;
        int p = 1;

        String hashedPassword  ="";

        if (verifyPassword(key)) {
             hashedPassword = SCryptUtil.scrypt(key, N, r, p);
        } else {
            System.out.println("Does not fulfill NIST.");
        }
        return hashedPassword;
    }

    public static boolean verifyPassword(String password) {
        boolean min8Char = password.length() >= 8;
        boolean max64Char = password.length() <= 64;

        Pattern p = Pattern.compile("[^a-zA-Z0-9 ]", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(password);
        //returns false if it does not have a special char in the passoword
        boolean hasSpecialChar = m.find();

        return min8Char && max64Char && hasSpecialChar && repetCharater(password) && isStringUpperCase(password) && isStringLowerCase(password);
    }

    /**
     * Checks that the str has a uppercase
     */
    private static boolean isStringUpperCase(String str){
        //convert String to char array
        char[] charArray = str.toCharArray();
        boolean isUpperCase = false;

        for (char c : charArray) {
            //if any character is not in upper case, return false
            if (Character.isUpperCase(c)) isUpperCase = true;
        }
        return isUpperCase;
    }

    /**
     * Checks that ut str ha a lower case
     */
    private static boolean isStringLowerCase(String str){

        //convert String to char array
        char[] charArray = str.toCharArray();
        boolean isLowerCase = false;

        for (char c : charArray) {
            //if any character is not in upper case, return false
            if (Character.isLowerCase(c)) isLowerCase = true;
        }
        return isLowerCase;
    }

    /**
     * checks that the key does not have more than 3 of the same char
     */
    public static boolean repetCharater(String key){
        Map<Character,Integer> map = new HashMap<Character,Integer>();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (map.containsKey(c)) {
                int cnt = map.get(c);
                map.put(c, ++cnt);
            } else {
                map.put(c, 1);
            }
        }
        for(int i = 0; i<key.length(); i++){
            if(map.get(key.charAt(i))>3){
                return false;
            }
        }
        return true;
    }

    public static String getSalt(){
        // chose a Character random from this String
        String AlphaNumericString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                + "0123456789"
                + "abcdefghijklmnopqrstuvxyz";

        // create StringBuffer size of AlphaNumericString
        StringBuilder salt = new StringBuilder(10);

        for (int i = 0; i < 10; i++) {

            // generate a random number between
            // 0 to AlphaNumericString variable length
            int index
                    = (int)(AlphaNumericString.length()
                    * Math.random());

            // add Character one by one in end of salt
            salt.append(AlphaNumericString
                    .charAt(index));
        }
        return salt.toString();
    }

    /**
     * Checks the hashed password to the plain text password match.
     */
    public static boolean check(String Password, String hash){
        return SCryptUtil.check(Password, hash);
    }
}
