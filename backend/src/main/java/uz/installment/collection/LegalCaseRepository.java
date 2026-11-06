package uz.installment.collection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LegalCaseRepository extends JpaRepository<LegalCase, Long> {

    Optional<LegalCase> findFirstByContractIdAndStatusNot(Long contractId, LegalCase.Status status);
}
