package CleanVision.web;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.MockMvc;

import CleanVision.repository.UserRepository;

/**
 * Registration and login without mobile OTP: the account is created as soon as the
 * form is valid, no verification screen exists, and nothing OTP-related is reachable
 * over HTTP.
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

    @Test
    void registerPageRendersAPlainForm() throws Exception {
        mvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String html = result.getResponse().getContentAsString();
                    assertThat(html).contains("Mobile number");
                    assertThat(html).doesNotContain("Send OTP").doesNotContain("otp-box");
                    assertThat(html).doesNotContain("id=\"submitBtn\" disabled");
                });
    }

    @Test
    void accountIsCreatedWithoutAnyOtpStep() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/register")
                        .session(session)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "No Otp Needed")
                        .param("email", "no-otp@example.com")
                        .param("phone", "9876543210")
                        .param("password", "secret123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/my-reports"));

        assertThat(users.findByEmailIgnoreCase("no-otp@example.com"))
                .isPresent()
                .get().satisfies(user -> {
                    assertThat(user.getPhone()).isEqualTo("9876543210");
                    assertThat(user.isPhoneVerified()).isTrue();
                });
    }

    @Test
    void apiRegisterWorksWithoutAnyOtpStep() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/api/auth/register")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Api Citizen\",\"email\":\"api-no-otp@example.com\","
                                + "\"phone\":\"9876543213\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("\"success\":true"));

        assertThat(users.existsByEmailIgnoreCase("api-no-otp@example.com")).isTrue();
    }

    @Test
    void invalidMobileIsStillRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/register")
                        .session(session)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Bad Mobile")
                        .param("email", "bad-mobile@example.com")
                        .param("phone", "12345")
                        .param("password", "secret123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register"));

        assertThat(users.existsByEmailIgnoreCase("bad-mobile@example.com")).isFalse();
    }

    @Test
    void loginIsNeverGatedBehindVerification() throws Exception {
        CleanVision.domain.User legacy = new CleanVision.domain.User();
        legacy.setName("Legacy Citizen");
        legacy.setEmail("legacy-plain@example.com");
        legacy.setPhone("9876543299");
        legacy.setRole(CleanVision.domain.Role.CITIZEN);
        legacy.setPhoneVerified(false);
        legacy.setPasswordHash(passworder.hash("secret123"));
        users.save(legacy);

        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "legacy-plain@example.com")
                        .param("password", "secret123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/my-reports"));

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
    void otpEndpointsAndScreenAreGone() throws Exception {
        mvc.perform(post("/api/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"9876543210\",\"purpose\":\"REGISTER\"}"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"9876543210\",\"code\":\"123456\"}"))
                .andExpect(status().isNotFound());

        // No longer public: anonymous traffic is sent back to the login screen.
        mvc.perform(get("/verify-mobile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?next=%2Fverify-mobile"));
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
