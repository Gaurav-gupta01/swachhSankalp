package CleanVision.sms;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Fallback delivery: writes the code to the application log. Used when MSG91 is not
 * configured (local development) or when the provider call fails, so registration is
 * never silently broken.
 */
@Component("loggingOtpSender")
@Slf4j
public class LoggingOtpSender implements OtpSender {

    @Override
    public boolean send(String mobile, String code, int expiryMinutes) {
        log.warn("OTP FALLBACK DELIVERY — SMS gateway unavailable. mobile=+91{} code={} expires in {} min",
                mobile, code, expiryMinutes);
        return true;
    }
}
