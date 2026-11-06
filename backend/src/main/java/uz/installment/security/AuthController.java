package uz.installment.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AccessService access;

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, long expiresIn, UserView user) {
    }

    /**
     * permissions — amaldagi ruxsatlar; customPermissions — xodimga alohida ruxsatlar berilganmi
     * (false bo'lsa rol ruxsatlari ishlaydi).
     */
    public record UserView(Long id, String username, String fullName, Role role, boolean active,
                           Long branchId, String branchName, List<Permission> permissions,
                           boolean customPermissions) {
        static UserView of(User u, Set<Permission> effective) {
            return new UserView(u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.isActive(),
                    u.getBranch() != null ? u.getBranch().getId() : null,
                    u.getBranch() != null ? u.getBranch().getName() : null,
                    effective.stream().sorted().toList(),
                    !u.getRole().isAdmin() && u.getPermissions() != null);
        }
    }

    UserView view(User u) {
        return UserView.of(u, access.effective(u.getRole(), u.getPermissions()));
    }

    @PostMapping("/login")
    @Transactional(readOnly = true)
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        User user = users.findByUsername(req.username().trim())
                .filter(User::isActive)
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login yoki parol noto'g'ri"));
        access.invalidate(user.getId());
        return new LoginResponse(jwtService.issue(user), jwtService.ttlSeconds(), view(user));
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public UserView me(@AuthenticationPrincipal AuthUser me) {
        return users.findById(me.id()).map(this::view)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
