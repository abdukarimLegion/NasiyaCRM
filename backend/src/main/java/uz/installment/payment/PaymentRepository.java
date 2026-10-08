package uz.installment.payment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByMethodAndExternalId(PaymentMethod method, String externalId);

    List<Payment> findByContractIdOrderByPaidAtDesc(Long contractId);

    @EntityGraph(attributePaths = {"contract", "contract.client", "cashier"})
    @Query("select p from Payment p where p.paidAt >= :from and p.paidAt < :to")
    Page<Payment> findInPeriod(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
