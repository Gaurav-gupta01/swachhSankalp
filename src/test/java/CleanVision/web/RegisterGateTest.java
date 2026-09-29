package CleanVision.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import CleanVision.repository.UserRepository;
import CleanVision.service.OtpService;

/**
 * Web-layer checks for the OTP registration gate: the form is shown, an account cannot
 * be created before the mobile is proven, and the API cannot be used to skip the gate.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegisterGateTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private CleanVision.security.PasswordHasher passworder;

    @MockitoBean
    private OtpService otpService;

    @Test
    void registerPageRendersTheOtpForm() throws Exception {
        mvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String html = result.getResponse().getContentAsString();
                    assertThat(html).contains("Mobile number");
                    assertThat(html).contains("Send OTP");
                    assertThat(html).contains("Verify ✓");
                    assertThat(html).contains("id=\"emailHint\"");
                    assertThat(html).contains("id=\"submitBtn\" disabled");
                });
    }

    @Test
    void accountIsNotCreatedBeforeTheMobileIsVerified() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/register")
                        .session(session)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Gate Jumped")
                        .param("email", "gate-jumped@example.com")
                        .param("phone", "9876543210")
                        .param("password", "secret123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register"));

        assertThat(users.existsByEmailIgnoreCase("gate-jumped@example.com")).isFalse();
    }

    @Test
    void apiRegisterIsRejectedWithoutAProvenMobile() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/api/auth/register")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Api Jump\",\"email\":\"api-jump@example.com\","
                                + "\"phone\":\"9876543211\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("\"success\":false")
                        .contains("Verify your mobile number"));

        assertThat(users.existsByEmailIgnoreCase("api-jump@example.com")).isFalse();
    }

    @Test
    void verifiedMobileUnlocksRegistration() throws Exception {
        when(otpService.verify(anyString(), anyString(), anyString())).thenReturn(true);

        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/api/auth/otp/send")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"9876543212\",\"purpose\":\"REGISTER\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("\"success\":true"));

        mvc.perform(post("/api/auth/otp/verify")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"9876543212\",\"code\":\"123456\",\"purpose\":\"REGISTER\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("\"success\":true"));

        mvc.perform(post("/register")
                        .session(session)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Verified Citizen")
                        .param("email", "verified@example.com")
                        .param("phone", "+91 98765 43212")
                        .param("password", "secret123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/my-reports"));

        assertThat(users.findByEmailIgnoreCase("verified@example.com"))
                .isPresent()
                .get().satisfies(user -> {
                    assertThat(user.isPhoneVerified()).isTrue();
                    assertThat(user.getPhone()).isEqualTo("9876543212");
                });
    }

    @Test
    void loginGateSendsUnverifiedCitizensToVerification() throws Exception {
        CleanVision.domain.User legacy = new CleanVision.domain.User();
        legacy.setName("Legacy Citizen");
        legacy.setEmail("legacy-gate@example.com");
        legacy.setPhone("9876543299");
        legacy.setRole(CleanVision.domain.Role.CITIZEN);
        legacy.setPhoneVerified(false);
        legacy.setPasswordHash(passworder.hash("secret123"));
        users.save(legacy);

        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "legacy-gate@example.com")
                        .param("password", "secret123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/verify-mobile"));

        // The seeded officer account is staff — verified at creation, straight to the dashboard.
        MockHttpSession officer = new MockHttpSession();
        mvc.perform(post("/login")
                        .session(officer)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "admin@cleanvision.gov.in")
                        .param("password", "admin123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    void wrongPasswordStillReportsAnError() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "nobody@example.com")
                        .param("password", "nope123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=1"));
    }
}
