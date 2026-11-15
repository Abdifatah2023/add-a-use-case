package team.projectpulse.student;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.system.StatusCode;
import team.projectpulse.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-STU-deactivate-student: PUT /students/{studentId}/deactivate, main flow, 6a, and 6b. Step 4 and 5a are the
 * SPA's confirm dialog and send no request (frontend/cypress/e2e/deactivatestudent.spec.cy.js).
 *
 * <p>Seed data: John (id 4) is on Team1 in course section 2023-2024 of COSC 40993, whose course admin is Bingyang;
 * Bingyang and Bill are both assigned to that course section; Tim is the course admin of another course.
 */
@DisplayName("Integration tests for deactivating a student")
class StudentDeactivationIntegrationTest extends AbstractIntegrationTest {

    private static final int JOHN = 4;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StudentRepository studentRepository;

    @Autowired
    UserRepository userRepository;

    @Value("${api.endpoint.base-url}")
    String baseUrl;

    String adminBingyangToken;
    String instructorBillToken;
    String adminTimToken;
    String studentJohnToken;


    @BeforeEach
    void setUp() throws Exception {
        this.adminBingyangToken = login("b.wei@abc.edu");
        this.instructorBillToken = login("b.gates@abc.edu");
        this.adminTimToken = login("t.cook@abc.edu");
        this.studentJohnToken = login("j.smith@abc.edu");
    }

    private String login(String username) throws Exception {
        String body = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(username, "123456")))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + new JSONObject(body).getJSONObject("data").getString("token");
    }

    private ResultActions deactivate(int studentId, String token) throws Exception {
        return this.mockMvc.perform(put(this.baseUrl + "/students/" + studentId + "/deactivate").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, token));
    }

    private void unassignFromJohnsSection(String instructorUsername) {
        Instructor instructor = (Instructor) this.userRepository.findByUsername(instructorUsername).orElseThrow();
        this.studentRepository.findById(JOHN).orElseThrow().getSection().removeInstructor(instructor);
    }

    private boolean johnIsActive() {
        return this.studentRepository.findById(JOHN).orElseThrow().isEnabled();
    }

    @Test
    void testAssignedInstructorDeactivatesStudent() throws Exception {
        Student before = this.studentRepository.findById(JOHN).orElseThrow();
        Integer sectionId = before.getSection().getSectionId();
        Integer teamId = before.getTeam().getTeamId();

        deactivate(JOHN, this.instructorBillToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.message").value("Deactivate student successfully"))
                .andExpect(jsonPath("$.data.id").value(JOHN))
                .andExpect(jsonPath("$.data.enabled").value(false))
                // BR-student-lifecycle: she stays in her course section and on her team
                .andExpect(jsonPath("$.data.sectionId").value(sectionId))
                .andExpect(jsonPath("$.data.teamId").value(teamId));

        Student after = this.studentRepository.findById(JOHN).orElseThrow();
        assertThat(after.isEnabled()).isFalse();
        assertThat(after.getSection().getSectionId()).isEqualTo(sectionId);
        assertThat(after.getTeam().getTeamId()).isEqualTo(teamId);
    }

    @Test
    void testOwningCourseAdminNotAssignedToTheSectionDeactivatesStudent() throws Exception {
        // In the seed data Bingyang is also assigned to John's course section, so take her off it first: the course
        // admin who owns the course is admitted through ownership alone (BR-section-scoped-access)
        unassignFromJohnsSection("b.wei@abc.edu");

        deactivate(JOHN, this.adminBingyangToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
        assertThat(johnIsActive()).isFalse();
    }

    @Test
    void testDeactivateAnAlreadyDeactivatedStudent() throws Exception {
        // 6a
        deactivate(JOHN, this.instructorBillToken).andExpect(status().isOk());

        deactivate(JOHN, this.instructorBillToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.CONFLICT))
                .andExpect(jsonPath("$.message").value("This student is already deactivated."));
        assertThat(johnIsActive()).isFalse();
    }

    @Test
    void testInstructorNotAssignedToTheSectionCannotDeactivate() throws Exception {
        // 6b: Bill teaches in the course but is no longer assigned to John's course section
        unassignFromJohnsSection("b.gates@abc.edu");

        deactivate(JOHN, this.instructorBillToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN))
                .andExpect(jsonPath("$.message").value("No permission."));
        assertThat(johnIsActive()).isTrue();
    }

    @Test
    void testCourseAdminOfAnotherCourseCannotDeactivate() throws Exception {
        // 6b
        deactivate(JOHN, this.adminTimToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN))
                .andExpect(jsonPath("$.message").value("No permission."));
        assertThat(johnIsActive()).isTrue();
    }

    @Test
    void testStudentCannotDeactivateHerself() throws Exception {
        deactivate(JOHN, this.studentJohnToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));
        assertThat(johnIsActive()).isTrue();
    }

    @Test
    void testUnknownStudentIdIsForbidden() throws Exception {
        // The managers fail closed and do not reveal which ids exist
        deactivate(99999, this.adminBingyangToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN))
                .andExpect(jsonPath("$.message").value("No permission."));
    }

    @Test
    void testOutOfScopeCallerIsRefusedEvenWhenTheStudentIsAlreadyDeactivated() throws Exception {
        // The route check runs before the service, so 6b wins over 6a
        this.studentRepository.findById(JOHN).orElseThrow().setEnabled(false);

        deactivate(JOHN, this.adminTimToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));
    }

}
