package team.projectpulse.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import team.projectpulse.student.Student;
import team.projectpulse.user.UserRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ActiveAccountJwtAuthenticationConverterTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ActiveAccountJwtAuthenticationConverter converter =
            new ActiveAccountJwtAuthenticationConverter(new JwtAuthenticationConverter(), this.userRepository);

    private static Jwt jwt(Object userId) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256").subject("j.smith@abc.edu")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (userId != null) {
            builder.claim("userId", userId);
        }
        return builder.build();
    }

    @Test
    void testActiveAccountIsConverted() {
        given(this.userRepository.findById(4)).willReturn(Optional.of(new Student("j.smith@abc.edu", "John", "Smith", "j.smith@abc.edu", "123456", true, "student")));

        AbstractAuthenticationToken token = this.converter.convert(jwt(4L));

        assertThat(token.getName()).isEqualTo("j.smith@abc.edu");
    }

    @Test
    void testDeactivatedAccountIsRefused() {
        given(this.userRepository.findById(4)).willReturn(Optional.of(new Student("j.smith@abc.edu", "John", "Smith", "j.smith@abc.edu", "123456", false, "student")));

        assertThatThrownBy(() -> this.converter.convert(jwt(4L))).isInstanceOf(DisabledException.class);
    }

    @Test
    void testAccountThatNoLongerExistsIsRefused() {
        given(this.userRepository.findById(4)).willReturn(Optional.empty());

        assertThatThrownBy(() -> this.converter.convert(jwt(4L))).isInstanceOf(DisabledException.class);
    }

    @Test
    void testTokenWithoutAUserIdIsRefused() {
        assertThatThrownBy(() -> this.converter.convert(jwt(null))).isInstanceOf(DisabledException.class);
    }

}
