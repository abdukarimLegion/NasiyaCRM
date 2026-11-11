package uz.installment.client;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    boolean existsByPinfl(String pinfl);

    boolean existsByPinflAndIdNot(String pinfl, Long id);

    /** Kredit limitini parallel shartnomalar buzmasligi uchun mijoz qatorini qulflaydi. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Client c where c.id = :id")
    Optional<Client> findForUpdate(@Param("id") Long id);

    @Query("""
            select c from Client c
            where (:q = ''
               or lower(c.fullName) like lower(concat('%', :q, '%'))
               or c.pinfl like concat('%', :q, '%')
               or c.phone like concat('%', :q, '%'))
              and (:blacklisted = false or c.blacklisted = true)
              and (:withDebt = false or exists (select 1 from Contract k where k.client = c
                     and k.status in (uz.installment.contract.ContractStatus.ACTIVE,
                                      uz.installment.contract.ContractStatus.LATE)))
              and (:risk is null or exists (select 1 from Contract k2 where k2.client = c and k2.riskCategory = :risk
                     and k2.id = (select max(k3.id) from Contract k3 where k3.client = c)))
            """)
    Page<Client> search(@Param("q") String q, @Param("risk") uz.installment.scoring.ScoringEngine.RiskCategory risk,
                        @Param("withDebt") boolean withDebt, @Param("blacklisted") boolean blacklisted,
                        Pageable pageable);
}
