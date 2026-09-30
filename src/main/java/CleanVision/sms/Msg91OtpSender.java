package CleanVision.sms;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends the OTP through MSG91's SendOTP HTTP API. Credentials come from the environment
 * only ({@code MSG91_AUTH_KEY}, {@code MSG91_SENDER_ID}) and are never committed.
 *
 * <p>The code is passed explicitly so the hash stored in our own database stays
 * authoritative — verification always happens locally, never at the provider.</p>
 */
@Component("msg91OtpSender")
@Slf4j
public class Msg91OtpSender implements OtpSender {

    @Getter
    private final String authKey;

    private final String senderId;
    private final String baseUrl;
    private final String dltTeId;
    private final String message;

    public Msg91OtpSender(
            @Value("${cleanvision.msg91.auth-key:}") String authKey,
            @Value("${cleanvision.msg91.sender-id:}") String senderId,
            @Value("${cleanvision.msg91.base-url:https://control.msg91.com}") String baseUrl,
            @Value("${cleanvision.msg91.dlt-te-id:}") String dltTeId,
            @Value("${cleanvision.msg91.message:}") String message) {
        this.authKey = authKey == null ? "" : authKey.trim();
        this.senderId = senderId == null ? "" : senderId.trim();
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://control.msg91.com" : baseUrl.trim();
        this.dltTeId = dltTeId == null ? "" : dltTeId.trim();
        this.message = message == null ? "" : message;
    }

    public boolean isConfigured() {
        return !authKey.isEmpty();
    }

    @Override
    public boolean send(String mobile, String code, int expiryMinutes) {
        if (!isConfigured()) {
            return false;
        }
        try {
            String form = "authkey=" + encode(authKey)
                    + "&mobile=91" + encode(mobile)
                    + "&otp=" + encode(code)
                    + "&otp_expiry=" + expiryMinutes
                    + "&otp_length=" + code.length()
                    + (senderId.isEmpty() ? "" : "&sender=" + encode(senderId))
                    // Indian DLT rules: the approved template id must travel with the SMS.
                    + (dltTeId.isEmpty() ? "" : "&DLT_TE_ID=" + encode(dltTeId))
                    + (message.isEmpty() ? "" : "&message=" + encode(message));

            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl + "/api/sendotp.php"))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(form))
                    .build();

            java.net.http.HttpResponse<String> response =
                    client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            String body = response.body() == null ? "" : response.body();
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300
                    && body.toLowerCase(java.util.Locale.ROOT).contains("\"type\":\"success\"");
            if (!success) {
                log.warn("MSG91 rejected the OTP SMS (HTTP {}): {}", response.statusCode(), body);
            }
            return success;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("MSG91 OTP request interrupted: {}", ex.getMessage());
            return false;
        } catch (Exception ex) {
            log.warn("MSG91 OTP request failed: {}", ex.toString());
            return false;
        }
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
