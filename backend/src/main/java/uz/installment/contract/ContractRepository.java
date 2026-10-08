package uz.installment.contract;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import uz.installment.scoring.ScoringEngine;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    @Query(value = "select nextval('contract_no_seq')", nativeQuery = true)
    long nextContractSeq();

    long countByClientIdAndStatusIn(Long clientId, Collection<ContractStatus> statuses);

    boolean existsByClientIdAndStatus(Long clientId, ContractStatus status);

    /** openOnly — faqat to'lov qabul qilinadigan (ACTIVE / LATE) shartnomalar (kassa uchun). */
    @EntityGraph(attributePaths = "client")
    @Query("""
            select c from Contract c
            where (:status is null or c.status = :status)
              and (:risk is null or c.riskCategory = :risk)
              and (:openOnly = false or c.status in (uz.installment.contract.ContractStatus.ACTIVE,
                                                     uz.installment.contract.ContractStatus.LATE))
              and (:q = '' or lower(c.contractNo) like lower(concat('%', :q, '%'))
                           or lower(c.client.fullName) like lower(concat('%', :q, '%'))
                           or c.client.phone like concat('%', :q, '%'))
            """)
    Page<Contract> search(@Param("status") ContractStatus status, @Param("risk") ScoringEngine.RiskCategory risk,
                          @Param("openOnly") boolean openOnly, @Param("q") String q, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "schedule"})
    @Query("select c from Contract c where c.id = :id")
    Optional<Contract> findWithSchedule(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contract c where c.id = :id")
    Optional<Contract> findForUpdate(@Param("id") Long id);

    /** Kunlik job: muddati o'tgan qismi bor faol shartnomalarni LATE qiladi. */
    @Modifying
    @Transactional
    @Query(value = """
            update contracts c set status = 'LATE', updated_at = now()
            where c.status = 'ACTIVE'
              and exists (select 1 from schedule_items s
                          where s.contract_id = c.id and s.status <> 'PAID' and s.due_date < :today)
            """, nativeQuery = true)
    int markLateContracts(@Param("today") LocalDate today);
}
