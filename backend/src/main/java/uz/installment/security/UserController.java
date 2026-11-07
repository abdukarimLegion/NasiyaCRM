package uz.installment.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import uz.installment.branch.BranchRepository;
import uz.installment.common.BusinessException;
import uz.installment.common.NotFoundException;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository users;
    private final BranchRepository branches;
    private final PasswordEncoder passwordEncoder;
    private final AccessService access;
    private final AuthController auth;

    /** permissions null bo'lsa — xodim rolining ruxsatlarini oladi. */
    public record CreateUser(@NotBlank @Size(max = 50) String username,
                             @NotBlank @Size(min = 8, max = 100) String password,
                             @NotBlank @Size(max = 150) String fullName,
                             @NotNull Role role, Long branchId, List<Permission> permissions,
                             Long customRoleId, @Valid Profile profile) {
    }

    public record UpdateUser(@NotBlank @Size(max = 150) String fullName, @NotNull Role role, boolean active,
                             @Size(min = 8, max = 100) String newPassword, Long branchId,
                             List<Permission> permissions, Long customRoleId, @Valid Profile profile) {
    }

    /** Xodim shaxsiy ma'lumotlari va nasiya cheklovi (komplayens). */
    public record Profile(@Pattern(regexp = "^[0-9]{14}$", message = "JShShIR 14 ta raqam") String pinfl,
                          @Size(max = 20) String passportSeries, java.time.LocalDate birthDate,
                          @Size(max = 300) String homeAddress, @Size(max = 20) String phone,
                          @Size(max = 150) String email, boolean allowNasiya, @Size(max = 300) String nasiyaNote) {
    }

    /** KPI, filial va mas'ul xodim tanlash uchun qisqa ro'yxat (barcha xodimlarga ochiq). */
    public record UserBrief(Long id, String fullName, Role role, Long branchId, boolean active) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    @Transactional(readOnly = true)
    public List<AuthController.UserView> list() {
        return users.findAllByOrderByFullNameAsc().stream().map(auth::view).toList();
    }

    @GetMapping("/brief")
    @Transactional(readOnly = true)
    public List<UserBrief> brief() {
        return users.findAllByOrderByFullNameAsc().stream()
                .map(u -> new UserBrief(u.getId(), u.getFullName(), u.getRole(),
                        u.getBranch() != null ? u.getBranch().getId() : null, u.isActive()))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    @Transactional
    public AuthController.UserView create(@Valid @RequestBody CreateUser req) {
        if (users.existsByUsername(req.username().trim())) {
            throw new BusinessException("USERNAME_TAKEN", "Bu login band: " + req.username());
        }
        User u = new User();
        u.setUsername(req.username().trim());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setFullName(req.fullName().trim());
        u.setRole(req.role());
        u.setBranch(req.branchId() == null ? null : branches.findById(req.branchId())
                .orElseThrow(() -> new NotFoundException("Filial", req.branchId())));
        u.setPermissions(permissionsFor(req.role(), req.permissions()));
        u.setCustomRoleId(req.role().isAdmin() ? null : req.customRoleId());
        applyProfile(u, req.profile());
        return auth.view(users.save(u));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    @Transactional
    public AuthController.UserView update(@PathVariable Long id, @Valid @RequestBody UpdateUser req,
                                          @AuthenticationPrincipal AuthUser me) {
        User u = users.findById(id).orElseThrow(() -> new NotFoundException("Foydalanuvchi", id));
        if (u.getId().equals(me.id()) && (!req.active() || req.role() != u.getRole())) {
            throw new BusinessException("SELF_LOCK", "O'zingizning rolingizni o'zgartira yoki hisobingizni o'chira olmaysiz");
        }
        u.setFullName(req.fullName().trim());
        u.setRole(req.role());
        u.setActive(req.active());
        u.setBranch(req.branchId() == null ? null : branches.findById(req.branchId())
                .orElseThrow(() -> new NotFoundException("Filial", req.branchId())));
        u.setPermissions(permissionsFor(req.role(), req.permissions()));
        u.setCustomRoleId(req.role().isAdmin() ? null : req.customRoleId());
        applyProfile(u, req.profile());
        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        }
        access.invalidate(u.getId());
        return auth.view(u);
    }

    private void applyProfile(User u, Profile p) {
        if (p == null) {
            return;
        }
        String pinfl = p.pinfl() == null || p.pinfl().isBlank() ? null : p.pinfl().trim();
        if (pinfl != null && users.existsByPinflAndIdNot(pinfl, u.getId() == null ? -1L : u.getId())) {
            throw new BusinessException("PINFL_TAKEN", "Bu JShShIR boshqa xodimda mavjud");
        }
        u.setPinfl(pinfl);
        u.setPassportSeries(blankToNull(p.passportSeries()));
        u.setBirthDate(p.birthDate());
        u.setHomeAddress(blankToNull(p.homeAddress()));
        u.setPhone(blankToNull(p.phone()));
        u.setEmail(blankToNull(p.email()));
        u.setAllowNasiya(p.allowNasiya());
        u.setNasiyaNote(blankToNull(p.nasiyaNote()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static List<String> permissionsFor(Role role, List<Permission> requested) {
        if (role.isAdmin() || requested == null) {
            return null;
        }
        return requested.stream().distinct().sorted().map(Enum::name).toList();
    }
}
