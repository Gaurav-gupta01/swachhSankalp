package CleanVision.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The navbar follows the signed-in role instead of the public site: each dashboard shows
 * its own links, and the auth screens carry neither navbar nor footer.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NavbarTest {

    @Autowired
    private MockMvc mvc;

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

    private String page(MockHttpSession session, String url) throws Exception {
        var request = get(url);
        if (session != null) {
            request = request.session(session);
        }
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return result.getResponse().getContentAsString();
    }

    /** Only the top bar — the marketing footer is a separate concern. */
    private String navbar(MockHttpSession session, String url) throws Exception {
        String html = page(session, url);
        int start = html.indexOf("<header");
        int end = html.indexOf("</header>");
        assertThat(start).as("navbar missing from " + url).isGreaterThanOrEqualTo(0);
        return html.substring(start, end + "</header>".length());
    }

    @Test
    void visitorSeesThePublicNavbar() throws Exception {
        String html = navbar(null, "/");
        assertThat(html).contains("How It Works").contains("Login");
        assertThat(html).doesNotContain("My Reports");
    }

    @Test
    void citizenSeesTheirOwnNavbarNotTheHomePageLinks() throws Exception {
        MockHttpSession citizen = signIn("citizen@demo.in", "demo123");
        String html = navbar(citizen, "/my-reports");

        assertThat(html).contains("My Reports")
                .contains("Report Waste")
                .contains("Report Animal")
                .doesNotContain("How It Works")
                .contains("href=\"/my-reports\"");
    }

    @Test
    void officerSeesTheAdminNavbar() throws Exception {
        MockHttpSession officer = signIn("admin@cleanvision.gov.in", "admin123");
        String html = navbar(officer, "/admin");

        assertThat(html).contains("Analytics").contains("Teams").contains("Facilities")
                .doesNotContain("How It Works");
    }

    @Test
    void teamLeaderSeesTheQueueNavbar() throws Exception {
        MockHttpSession leader = signIn("leader@cleanvision.gov.in", "leader123");
        String html = navbar(leader, "/team");

        assertThat(html).contains("My Team Queue")
                .doesNotContain("How It Works")
                .doesNotContain("Analytics");
    }

    @Test
    void loginScreenHasNeitherNavbarNorFooter() throws Exception {
        String html = page(null, "/login");
        assertThat(html).doesNotContain("navbar-brand").doesNotContain("cv-footer");
        assertThat(html).contains("Back to home").contains("href=\"/\"");
    }
}
