package uz.installment.scoring;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.installment.client.Client;
import uz.installment.contract.ContractRepository;
import uz.installment.contract.ContractStatus;
import uz.installment.scoring.ScoringEngine.Factor;
import uz.installment.scoring.ScoringEngine.StopFactor;
import uz.installment.settings.SettingsService;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scoring + stop-faktorlar. Avtomatik tekshiriladigan stop-faktorlar bazadan olinadi;
 * FRAUD / COURT hozircha qo'lda belgilanadi — KATM / MIB integratsiyasidan keyin
 * shu yerda avtomatik to'ldiriladi.
 */
@Service
@RequiredArgsConstructor
public class ScoringService {

    /** Qo'lda belgilash mumkin bo'lgan stop-faktorlar (qolganlari tizim tomonidan aniqlanadi). */
    public static final Set<StopFactor> MANUAL_STOP_FACTORS =
            EnumSet.of(StopFactor.BLACKLIST, StopFactor.FRAUD, StopFactor.COURT);

    private final ContractRepository contracts;
    private final SettingsService settings;
    private final Clock clock;

    public record ScoringRequest(Map<Factor, Integer> points, boolean incomeVerified, boolean guarantorPresent,
                                 Set<StopFactor> manualStopFactors) {
    }

    @Transactional(readOnly = true)
    public ScoringEngine.Result evaluate(Client client, ScoringRequest req) {
        Set<StopFactor> stops = EnumSet.noneOf(StopFactor.class);
        if (req.manualStopFactors() != null) {
            req.manualStopFactors().stream().filter(MANUAL_STOP_FACTORS::contains).forEach(stops::add);
        }
        stops.addAll(automaticStopFactors(client));
        return ScoringEngine.evaluate(new ScoringEngine.Input(
                req.points(), req.incomeVerified(), req.guarantorPresent(), stops));
    }

    Set<StopFactor> automaticStopFactors(Client client) {
        Set<StopFactor> stops = EnumSet.noneOf(StopFactor.class);
        LocalDate today = LocalDate.now(clock);
        if (client.isBlacklisted()) {
            stops.add(StopFactor.BLACKLIST);
        }
        if (client.getPassportExpiry() != null && client.getPassportExpiry().isBefore(today)) {
            stops.add(StopFactor.PASSPORT_EXPIRED);
        }
        if (contracts.existsByClientIdAndStatus(client.getId(), ContractStatus.LATE)) {
            stops.add(StopFactor.OVERDUE);
        }
        long open = contracts.countByClientIdAndStatusIn(client.getId(),
                List.of(ContractStatus.ACTIVE, ContractStatus.LATE));
        if (open >= settings.current().getMaxActiveContracts()) {
            stops.add(StopFactor.OVERLIMIT);
        }
        return stops;
    }
}
