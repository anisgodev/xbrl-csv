package eu.registredora.xbrlcsv;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Legal Entity Identifier (ISO 17442): 18 alphanumeric characters followed by 2 check digits computed with
 * ISO 7064 MOD 97-10.
 */
public record Lei(String value) {

    private static final Pattern FORMAT = Pattern.compile("[0-9A-Z]{18}[0-9]{2}");

    public Lei {
        Objects.requireNonNull(value, "value");
        if (!isValid(value)) {
            throw new IllegalArgumentException("Invalid LEI: " + value);
        }
    }

    public static Lei of(String value) {
        return new Lei(value == null ? null : value.strip().toUpperCase());
    }

    /** True if the string has the format of a LEI and correct check digits. */
    public static boolean isValid(String value) {
        if (value == null || !FORMAT.matcher(value).matches()) {
            return false;
        }
        return mod97(value) == 1;
    }

    /** Computes the two check digits for an 18-character prefix. */
    public static String checkDigits(String prefix18) {
        if (prefix18 == null || !prefix18.matches("[0-9A-Z]{18}")) {
            throw new IllegalArgumentException("The prefix must contain 18 uppercase alphanumeric characters");
        }
        int check = 98 - mod97(prefix18 + "00");
        return String.format("%02d", check);
    }

    private static int mod97(String s) {
        int remainder = 0;
        for (int i = 0; i < s.length(); i++) {
            int digit = Character.digit(s.charAt(i), 36);
            remainder = (digit > 9 ? remainder * 100 + digit : remainder * 10 + digit) % 97;
        }
        return remainder;
    }

    @Override
    public String toString() {
        return value;
    }
}
