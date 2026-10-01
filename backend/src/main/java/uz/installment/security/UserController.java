package uz.installment.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.common.BusinessException;
import uz.installment.common.NotFoundException;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public record CreateUser(@NotBlank @Size(max = 50) String username,
                             @NotBlank @Size(min = 8, max = 100) String password,
                             @NotBlank @Size(max = 150) String fullName,
                             @NotNull Role role) {
    }

    public record UpdateUser(@NotBlank @Size(max = 150) String fullName, @NotNull Role role, boolean active,
                             @Size(min = 8, max = 100) String newPassword) {
    }

    @GetMapping
    public List<AuthController.UserView> list() {
        return users.findAllByOrderByFullNameAsc().stream().map(AuthController.UserView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AuthController.UserView create(@Valid @RequestBody CreateUser req) {
        if (users.existsByUsername(req.username())) {
            throw new BusinessException("USERNAME_TAKEN", "Bu login band: " + req.username());
        }
        User u = new User();
        u.setUsername(req.username().trim());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setFullName(req.fullName().trim());
        u.setRole(req.role());
        return AuthController.UserView.of(users.save(u));
    }

    @PutMapping("/{id}")
    @Transactional
    public AuthController.UserView update(@PathVariable Long id, @Valid @RequestBody UpdateUser req) {
        User u = users.findById(id).orElseThrow(() -> new NotFoundException("Foydalanuvchi", id));
        u.setFullName(req.fullName().trim());
        u.setRole(req.role());
        u.setActive(req.active());
        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        }
        return AuthController.UserView.of(u);
    }
}
