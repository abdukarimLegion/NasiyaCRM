package uz.installment.scoring;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.installment.client.ClientService;
import uz.installment.scoring.ScoringEngine.Factor;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/scoring")
@RequiredArgsConstructor
public class ScoringController {

    private final ScoringService scoring;
    private final ClientService clients;

    public record EvaluateRequest(@NotNull Long clientId, @NotNull @Valid ScoringService.ScoringRequest scoring) {
    }

    public record FactorDto(Factor key, int max) {
    }

    /** Wizard'ning "Scoring" va "Stop-faktor" qadamlari uchun jonli baholash (hech narsa saqlanmaydi). */
    @PostMapping("/evaluate")
    public ScoringEngine.Result evaluate(@Valid @RequestBody EvaluateRequest req) {
        return scoring.evaluate(clients.get(req.clientId()), req.scoring());
    }

    @GetMapping("/factors")
    public List<FactorDto> factors() {
        return Arrays.stream(Factor.values()).map(f -> new FactorDto(f, f.max())).toList();
    }
}
