package uz.installment.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.installment.common.NotFoundException;
import uz.installment.common.TenantContext;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final TenantSettingsRepository repo;

    @Transactional(readOnly = true)
    public TenantSettings current() {
        long tenant = TenantContext.currentTenantId();
        return repo.findById(tenant).orElseThrow(() -> new NotFoundException("Sozlamalar", tenant));
    }

    @Transactional
    public TenantSettings update(SettingsController.SettingsDto dto) {
        TenantSettings s = repo.findById(TenantContext.currentTenantId())
                .orElseThrow(() -> new NotFoundException("Sozlamalar", TenantContext.currentTenantId()));
        s.setCompanyName(dto.companyName());
        s.setBrandName(dto.brandName());
        s.setPrimaryColor(dto.primaryColor());
        s.setLogoUrl(dto.logoUrl());
        s.setContractPrefix(dto.contractPrefix());
        s.setDefaultLang(dto.defaultLang());
        s.setRoundingStep(dto.roundingStep());
        s.setMaxActiveContracts(dto.maxActiveContracts());
        s.setSmsSender(dto.smsSender());
        return s;
    }
}
