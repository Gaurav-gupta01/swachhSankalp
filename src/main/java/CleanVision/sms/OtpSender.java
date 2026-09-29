package CleanVision.sms;

/**
 * Delivers a mobile OTP. Implementations decide how — MSG91 in production, the
 * application log when no gateway is configured or the provider call fails.
 */
public interface OtpSender {

    /**
     * @return true when the code was handed over to the channel successfully
     */
    boolean send(String mobile, String code, int expiryMinutes);
}
