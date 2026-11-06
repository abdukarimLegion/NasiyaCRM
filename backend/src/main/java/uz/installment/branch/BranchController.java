package uz.installment.branch;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.common.BusinessException;
import uz.installment.common.NotFoundException;
import uz.installment.security.AuthUser;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchRepository branches;
    private final BranchService service;

    public record BranchRequest(@NotBlank @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String code,
                                @NotBlank @Size(max = 150) String name,
                                @Size(max = 300) String address,
                                @Size(max = 30) String phone,
                                @Size(max = 150) String managerName,
                                @PositiveOrZero BigDecimal monthlyTarget,
                                boolean active) {
    }

    public record BranchDto(Long id, String code, String name, String address, String phone, String managerName,
                            BigDecimal monthlyTarget, boolean active) {
        static BranchDto of(Branch b) {
            return new BranchDto(b.getId(), b.getCode(), b.getName(), b.getAddress(), b.getPhone(),
                    b.getManagerName(), b.getMonthlyTarget(), b.isActive());
        }
    }

    /** Tanlash ro'yxatlari uchun — barcha xodimlarga ochiq. */
    @GetMapping
    public List<BranchDto> list() {
        return branches.findAllByOrderByActiveDescNameAsc().stream().map(BranchDto::of).toList();
    }

    /** Filiallar kesimidagi ko'rsatkichlar (month = YYYY-MM, bo'sh bo'lsa joriy oy). */
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('BRANCHES_VIEW')")
    public List<BranchService.BranchStats> stats(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @AuthenticationPrincipal AuthUser me) {
        return service.stats(month, me.branchScope(null));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BRANCHES_MANAGE')")
    @Transactional
    public BranchDto create(@Valid @RequestBody BranchRequest req) {
        String code = req.code().trim().toUpperCase();
        if (branches.existsByCode(code)) {
            throw new BusinessException("BRANCH_CODE_TAKEN", "Bu kod band: " + code);
        }
        Branch b = new Branch();
        apply(b, req, code);
        return BranchDto.of(branches.save(b));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BRANCHES_MANAGE')")
    @Transactional
    public BranchDto update(@PathVariable Long id, @Valid @RequestBody BranchRequest req) {
        Branch b = branches.findById(id).orElseThrow(() -> new NotFoundException("Filial", id));
        String code = req.code().trim().toUpperCase();
        if (branches.existsByCodeAndIdNot(code, id)) {
            throw new BusinessException("BRANCH_CODE_TAKEN", "Bu kod band: " + code);
        }
        apply(b, req, code);
        return BranchDto.of(b);
    }

    private static void apply(Branch b, BranchRequest req, String code) {
        b.setCode(code);
        b.setName(req.name().trim());
        b.setAddress(blank(req.address()));
        b.setPhone(blank(req.phone()));
        b.setManagerName(blank(req.managerName()));
        b.setMonthlyTarget(req.monthlyTarget());
        b.setActive(req.active());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
