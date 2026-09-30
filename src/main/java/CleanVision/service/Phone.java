package CleanVision.service;

/**
 * Indian mobile number normalisation. Registration, login and the admin user screens all
 * go through this so a number is stored as exactly ten digits.
 */
public final class Phone {

    private Phone() {
    }

    /**
     * Normalises an Indian mobile number to its 10 digits.
     * Accepts {@code 9876543210}, {@code +91 98765 43210}, {@code 919876543210}, {@code 09876543210}.
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AppException("Mobile number is required");
        }
        String digits = raw.trim().replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (digits.length() != 10 || !digits.matches("^[6-9][0-9]{9}$")) {
            throw new AppException("Enter a valid 10-digit Indian mobile number");
        }
        return digits;
    }

    public static boolean isValidMobile(String raw) {
        try {
            normalize(raw);
            return true;
        } catch (AppException ex) {
            return false;
        }
    }
}
