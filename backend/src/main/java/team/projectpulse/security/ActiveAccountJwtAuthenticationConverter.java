package team.projectpulse.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import team.projectpulse.user.UserRepository;

/**
 * Re-checks on every request that the account a bearer token names is still active (FR-SEC-active-account).
 *
 * <p>A token is self-contained and valid for two hours, and before this check only the login looked at
 * {@code enabled}, so a student deactivated mid-session (UC-STU-deactivate-student) kept full access until her token
 * expired. This converter loads the user named by the token's {@code userId} claim and throws
 * {@link DisabledException} when that account is deactivated or no longer exists. The resource-server filter routes
 * it through {@link CustomBearerTokenAuthenticationEntryPoint} to the {@code AccountStatusException} handler, a
 * {@code 401} the SPA turns into a logout. It applies to instructors as well, as the FR says.
 *
 * <p>The cost is one primary-key lookup per authenticated request. A token denylist or a per-user token version was
 * rejected because each needs a new table or column (see the key decisions in {@code docs/design/stu.md}).
 */
public class ActiveAccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtAuthenticationConverter delegate;
    private final UserRepository userRepository;


    public ActiveAccountJwtAuthenticationConverter(JwtAuthenticationConverter delegate, UserRepository userRepository) {
        this.delegate = delegate;
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Integer userId = readUserId(jwt);
        boolean active = userId != null && this.userRepository.findById(userId)
                .map(user -> user.isEnabled())
                .orElse(false);
        if (!active) {
            throw new DisabledException("This account is deactivated or no longer exists.");
        }
        return this.delegate.convert(jwt);
    }

    private static Integer readUserId(Jwt jwt) {
        Object claim = jwt.getClaim("userId");
        if (claim instanceof Number number) return number.intValue();
        if (claim instanceof String string) {
            try {
                return Integer.valueOf(string);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

}
