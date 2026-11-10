package uz.installment.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** USERS_MANAGE ruxsati berilgan (admin bo'lmagan) xodim o'z huquqini oshira olmasligi kerak. */
class UserGuardTest {

    private final AuthUser admin = new AuthUser(1L, "admin", Role.ADMIN, null, Role.ADMIN.defaultPermissions());
    private final AuthUser manager = new AuthUser(2L, "hr", Role.BRANCH_MANAGER, 5L,
            Set.of(Permission.USERS_MANAGE, Permission.CLIENTS_VIEW));

    @Test
    void nonAdminCannotCreatePromoteOrEditAdmin() {
        assertThatThrownBy(() -> UserController.guardAdminAccounts(manager, null, Role.ADMIN))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> UserController.guardAdminAccounts(manager, Role.CASHIER, Role.ADMIN))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> UserController.guardAdminAccounts(manager, Role.ADMIN, Role.ADMIN))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> UserController.guardAdminAccounts(manager, Role.ADMIN, Role.CASHIER))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nonAdminManagesOrdinaryStaffAndAdminManagesEveryone() {
        assertThatCode(() -> UserController.guardAdminAccounts(manager, null, Role.CASHIER)).doesNotThrowAnyException();
        assertThatCode(() -> UserController.guardAdminAccounts(manager, Role.CASHIER, Role.COLLECTOR))
                .doesNotThrowAnyException();
        assertThatCode(() -> UserController.guardAdminAccounts(admin, Role.ADMIN, Role.ADMIN)).doesNotThrowAnyException();
        assertThatCode(() -> UserController.guardAdminAccounts(admin, null, Role.ADMIN)).doesNotThrowAnyException();
    }

    @Test
    void nonAdminCannotGrantPermissionsTheyLack() {
        assertThatThrownBy(() -> UserController.guardGrantedPermissions(manager,
                List.of(Permission.CLIENTS_VIEW, Permission.SETTINGS_EDIT))).isInstanceOf(AccessDeniedException.class);
        assertThatCode(() -> UserController.guardGrantedPermissions(manager, List.of(Permission.CLIENTS_VIEW)))
                .doesNotThrowAnyException();
        assertThatCode(() -> UserController.guardGrantedPermissions(manager, null)).doesNotThrowAnyException();
        assertThatCode(() -> UserController.guardGrantedPermissions(admin, List.of(Permission.SETTINGS_EDIT)))
                .doesNotThrowAnyException();
    }
}
