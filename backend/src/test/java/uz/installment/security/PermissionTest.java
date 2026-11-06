package uz.installment.security;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionTest {

    @Test
    void adminHasEverything() {
        assertThat(Role.ADMIN.defaultPermissions()).containsExactlyInAnyOrderElementsOf(EnumSet.allOf(Permission.class));
    }

    @Test
    void defaultsKeepPreviousRoleBehaviour() {
        assertThat(Role.CREDIT_OFFICER.defaultPermissions())
                .contains(Permission.CONTRACTS_CREATE, Permission.CLIENTS_EDIT, Permission.PAYMENTS_ACCEPT,
                        Permission.COLLECTION_WORK)
                .doesNotContain(Permission.CONTRACTS_APPROVE, Permission.USERS_MANAGE, Permission.ALL_BRANCHES);
        assertThat(Role.CASHIER.defaultPermissions())
                .contains(Permission.PAYMENTS_ACCEPT)
                .doesNotContain(Permission.CONTRACTS_CREATE, Permission.COLLECTION_VIEW);
        assertThat(Role.COLLECTOR.defaultPermissions())
                .contains(Permission.COLLECTION_WORK, Permission.COLLECTION_LEGAL)
                .doesNotContain(Permission.PAYMENTS_ACCEPT);
    }

    @Test
    void unknownPermissionNamesAreIgnored() {
        assertThat(AccessService.parse(List.of("CLIENTS_VIEW", "REMOVED_ONE"))).containsExactly(Permission.CLIENTS_VIEW);
    }

    @Test
    void branchScope() {
        var own = new AuthUser(1L, "a", Role.CREDIT_OFFICER, 5L, Set.of(Permission.CONTRACTS_VIEW));
        assertThat(own.branchScope(null)).isEqualTo(5L);
        assertThat(own.branchScope(7L)).isEqualTo(5L);

        var all = new AuthUser(2L, "b", Role.CREDIT_OFFICER, 5L, Set.of(Permission.ALL_BRANCHES));
        assertThat(all.branchScope(null)).isNull();
        assertThat(all.branchScope(7L)).isEqualTo(7L);

        var hq = new AuthUser(3L, "c", Role.CASHIER, null, Set.of());
        assertThat(hq.branchScope(7L)).isEqualTo(7L);
    }
}
