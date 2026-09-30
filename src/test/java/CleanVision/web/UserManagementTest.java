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

import CleanVision.domain.Role;
import CleanVision.domain.User;
import CleanVision.repository.UserRepository;

/**
 * Super admin user management: only the super admin reaches /admin/users, and from there
 * an account can be created, edited and deleted — with the safety rails that keep the
 * super admin signed in and report history intact.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserManagementTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    private MockHttpSession signIn(String email, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().is3xxRedirection());
        return session;
    }

    @Test
    void onlyTheSuperAdminReachesUserManagement() throws Exception {
        MockHttpSession officer = signIn("admin@cleanvision.gov.in", "admin123");
        mvc.perform(get("/admin/users").session(officer))
                .andExpect(status().isForbidden());

        MockHttpSession superAdmin = signIn("super@cleanvision.gov.in", "super123");
        mvc.perform(get("/admin/users").session(superAdmin))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("User Management")
                        .contains("Create account"));
    }

    @Test
    void superAdminCanCreateEditAndDeleteAnAccount() throws Exception {
        MockHttpSession superAdmin = signIn("super@cleanvision.gov.in", "super123");

        mvc.perform(post("/admin/users")
                        .session(superAdmin)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Manageable User")
                        .param("email", "manageable@example.com")
                        .param("phone", "9876543201")
                        .param("password", "secret123")
                        .param("role", "CITIZEN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        User created = users.findByEmailIgnoreCase("manageable@example.com").orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.CITIZEN);
        assertThat(created.isPhoneVerified()).isTrue();
        Long id = created.getId();

        mvc.perform(get("/admin/users/" + id + "/edit").session(superAdmin))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("manageable@example.com")
                        .contains("Leave blank to keep the current password"));

        mvc.perform(post("/admin/users/" + id)
                        .session(superAdmin)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Renamed User")
                        .param("email", "renamed@example.com")
                        .param("phone", "9876543201")
                        .param("password", "")
                        .param("role", "ADMIN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        User updated = users.findById(id).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Renamed User");
        assertThat(updated.getEmail()).isEqualTo("renamed@example.com");
        assertThat(updated.getRole()).isEqualTo(Role.ADMIN);

        mvc.perform(post("/admin/users/" + id + "/delete")
                        .session(superAdmin))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        assertThat(users.findById(id)).isEmpty();
    }

    @Test
    void duplicateEmailAndLeaderWithoutTeamAreRejected() throws Exception {
        MockHttpSession superAdmin = signIn("super@cleanvision.gov.in", "super123");

        mvc.perform(post("/admin/users")
                        .session(superAdmin)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Copy Cat")
                        .param("email", "citizen@demo.in")
                        .param("password", "secret123")
                        .param("role", "CITIZEN"))
                .andExpect(redirectedUrl("/admin/users"));

        mvc.perform(post("/admin/users")
                        .session(superAdmin)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Leaderless")
                        .param("email", "leaderless@example.com")
                        .param("password", "secret123")
                        .param("role", "TEAM_LEADER"))
                .andExpect(redirectedUrl("/admin/users"));

        assertThat(users.existsByEmailIgnoreCase("leaderless@example.com")).isFalse();
    }

    @Test
    void superAdminCannotDeleteThemselvesOrReporters() throws Exception {
        MockHttpSession superAdmin = signIn("super@cleanvision.gov.in", "super123");
        User superUser = users.findByEmailIgnoreCase("super@cleanvision.gov.in").orElseThrow();
        User reporter = users.findByEmailIgnoreCase("citizen@demo.in").orElseThrow();

        mvc.perform(post("/admin/users/" + superUser.getId() + "/delete").session(superAdmin))
                .andExpect(redirectedUrl("/admin/users"));
        assertThat(users.findById(superUser.getId())).isPresent();

        // The demo citizen owns seeded reports, so the account must survive.
        mvc.perform(post("/admin/users/" + reporter.getId() + "/delete").session(superAdmin))
                .andExpect(redirectedUrl("/admin/users"));
        assertThat(users.findById(reporter.getId())).isPresent();
    }
}
