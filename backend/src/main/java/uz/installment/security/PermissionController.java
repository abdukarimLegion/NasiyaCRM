package uz.installment.security;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Rollar va ruxsatlar matritsasi (Sozlamalar → Rollar va ruxsatlar). */
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final AccessService access;

    public record PermissionInfo(Permission key, String section) {
    }

    /** customized — rol ruxsatlari standartdan farq qiladimi */
    public record RoleInfo(Role role, List<Permission> permissions, List<Permission> defaults, boolean customized,
                           boolean locked) {
    }

    public record Matrix(List<PermissionInfo> permissions, List<RoleInfo> roles) {
    }

    public record RoleUpdate(@NotNull List<Permission> permissions) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    public Matrix matrix() {
        List<PermissionInfo> perms = Arrays.stream(Permission.values())
                .map(p -> new PermissionInfo(p, p.section())).toList();
        Map<Role, RoleInfo> roles = new EnumMap<>(Role.class);
        access.allRoles().forEach((role, set) -> roles.put(role, new RoleInfo(role, set.stream().sorted().toList(),
                role.defaultPermissions().stream().sorted().toList(),
                !set.equals(role.defaultPermissions()), role.isAdmin())));
        return new Matrix(perms, List.copyOf(roles.values()));
    }

    @PutMapping("/roles/{role}")
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    public Matrix update(@PathVariable Role role, @RequestBody RoleUpdate req) {
        access.saveRole(role, req.permissions());
        return matrix();
    }

    @DeleteMapping("/roles/{role}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    public Matrix reset(@PathVariable Role role) {
        access.resetRole(role);
        return matrix();
    }
}
