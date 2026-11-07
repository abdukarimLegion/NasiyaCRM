package uz.installment.partner;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
import uz.installment.scoring.ScoringEngine.RiskCategory;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/partners")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerRepository partners;
    private final PartnerService service;

    public record PartnerDto(Long id, String code, String name, BigDecimal commissionPct, int maxTerm,
                             BigDecimal minDownPct, BigDecimal dtiPct, boolean active) {
        static PartnerDto of(Partner p) {
            return new PartnerDto(p.getId(), p.getCode(), p.getName(), p.getCommissionPct(), p.getMaxTerm(),
                    p.getMinDownPct(), p.getDtiPct(), p.isActive());
        }
    }

    public record PartnerRequest(@NotBlank @Pattern(regexp = "^[A-Z0-9_]{2,20}$") String code,
                                 @NotBlank @Size(max = 100) String name,
                                 @NotNull @PositiveOrZero @DecimalMax("50") BigDecimal commissionPct,
                                 @Min(1) @Max(60) int maxTerm,
                                 @NotNull @PositiveOrZero @DecimalMax("90") BigDecimal minDownPct,
                                 @NotNull @PositiveOrZero @DecimalMax("100") BigDecimal dtiPct, boolean active) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<PartnerDto> list() {
        return partners.findAllByOrderBySortOrderAscIdAsc().stream().map(PartnerDto::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SETTINGS_EDIT')")
    @Transactional
    public PartnerDto create(@Valid @RequestBody PartnerRequest r) {
        if (partners.findByCodeIgnoreCase(r.code()).isPresent()) {
            throw new BusinessException("PARTNER_CODE_TAKEN", "Bu kod band: " + r.code());
        }
        Partner p = new Partner();
        p.setCode(r.code());
        apply(p, r);
        p.setSortOrder((int) partners.count() + 1);
        return PartnerDto.of(partners.save(p));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SETTINGS_EDIT')")
    @Transactional
    public PartnerDto update(@PathVariable Long id, @Valid @RequestBody PartnerRequest r) {
        Partner p = partners.findById(id).orElseThrow(() -> new NotFoundException("Hamkor", id));
        apply(p, r);
        return PartnerDto.of(p);
    }

    private static void apply(Partner p, PartnerRequest r) {
        p.setName(r.name().trim());
        p.setCommissionPct(r.commissionPct());
        p.setMaxTerm(r.maxTerm());
        p.setMinDownPct(r.minDownPct());
        p.setDtiPct(r.dtiPct());
        p.setActive(r.active());
    }

    /**
     * Mijoz uchun ichki limit va hamkorlar takliflari + tavsiya. goodsTotal — mahsulotlar narxi,
     * internalAmount — ichki shartnoma bo'yicha mijoz to'laydigan jami (ustama bilan), bo'lmasa financed.
     */
    @GetMapping("/offers")
    @PreAuthorize("hasAuthority('CONTRACTS_CREATE')")
    public PartnerService.Offers offers(@RequestParam Long clientId, @RequestParam(required = false) RiskCategory risk,
                                        @RequestParam BigDecimal goodsTotal,
                                        @RequestParam(defaultValue = "0") BigDecimal downPayment,
                                        @RequestParam(required = false) BigDecimal internalAmount,
                                        @RequestParam(defaultValue = "12") int term) {
        return service.offers(clientId, risk, goodsTotal, downPayment, internalAmount, term);
    }
}
