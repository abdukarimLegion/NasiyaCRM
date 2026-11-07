package uz.installment.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, long expiresIn, UserView user) {
    }

    public record UserView(Long id, String username, String fullName, Role role, boolean active) {
        static UserView of(User u) {
            return new UserView(u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.isActive());
        }
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        String username = req.username().trim();
        String ip = request.getRemoteAddr();
        loginRateLimiter.check(ip, username);

        User user = users.findByUsername(username)
                .filter(User::isActive)
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> {
                    loginRateLimiter.failure(ip, username);
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login yoki parol noto'g'ri");
                });
        loginRateLimiter.success(ip, username);
        return new LoginResponse(jwtService.issue(user), jwtService.ttlSeconds(), UserView.of(user));
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal AuthUser me) {
        return users.findById(me.id()).filter(User::isActive).map(UserView::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
