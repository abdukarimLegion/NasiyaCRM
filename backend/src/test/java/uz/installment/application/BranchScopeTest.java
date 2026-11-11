package uz.installment.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.installment.branch.Branch;
import uz.installment.common.NotFoundException;
import uz.installment.contract.Contract;
import uz.installment.contract.ContractRepository;
import uz.installment.contract.ContractService;
import uz.installment.contract.ContractStatus;
import uz.installment.security.AuthUser;
import uz.installment.security.Permission;
import uz.installment.security.Role;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Boshqa filialning arizasi va shartnomasini id bo'yicha boshqarib bo'lmasligi kerak. */
@ExtendWith(MockitoExtension.class)
class BranchScopeTest {

    private final AuthUser otherBranchManager = new AuthUser(7L, "m1", Role.BRANCH_MANAGER, 1L,
            Role.BRANCH_MANAGER.defaultPermissions());
    private final AuthUser headOffice = new AuthUser(8L, "risk", Role.RISK_ANALYST, 1L,
            Set.of(Permission.CONTRACTS_APPROVE, Permission.ALL_BRANCHES));

    @Mock
    private ApplicationRepository applications;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private uz.installment.security.UserRepository users;
    @InjectMocks
    private ApplicationService service;

    private Application application;

    @BeforeEach
    void setUp() {
        Branch branch = mock(Branch.class);
        lenient().when(branch.getId()).thenReturn(2L);
        application = new Application();
        application.setBranch(branch);
        application.setStatus(ApplicationStatus.PENDING);
        lenient().when(applications.findForUpdate(10L)).thenReturn(Optional.of(application));
    }

    @Test
    void managerOfAnotherBranchCannotDecideApplication() {
        assertThatThrownBy(() -> service.approve(10L, otherBranchManager)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.reject(10L, "x", otherBranchManager)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.toContract(10L, otherBranchManager)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.handover(10L, "ALIF", null, null, null, null, otherBranchManager))
                .isInstanceOf(NotFoundException.class);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PENDING);
    }

    @Test
    void allBranchesPermissionStillWorks() {
        when(applications.findDetailed(10L)).thenReturn(Optional.of(application));
        service.approve(10L, headOffice);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
    }

    @Test
    void managerOfAnotherBranchCannotCancelContract() {
        ContractRepository contracts = mock(ContractRepository.class);
        Contract contract = mock(Contract.class);
        Branch branch = mock(Branch.class);
        when(branch.getId()).thenReturn(2L);
        when(contract.getBranch()).thenReturn(branch);
        when(contracts.findForUpdate(20L)).thenReturn(Optional.of(contract));
        ContractService contractService = new ContractService(contracts, null, null, null, null, null, null, null,
                null, null, null);

        assertThatThrownBy(() -> contractService.cancel(20L, otherBranchManager)).isInstanceOf(NotFoundException.class);
        verify(contract, never()).setStatus(ContractStatus.CANCELLED);
    }
}
