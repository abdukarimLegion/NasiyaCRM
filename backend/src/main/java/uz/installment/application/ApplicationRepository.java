package uz.installment.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    @Query(value = "select nextval('application_no_seq')", nativeQuery = true)
    long nextNo();

    @EntityGraph(attributePaths = {"client", "branch", "officer", "decidedBy", "contract"})
    @Query("""
            select a from Application a
            where (:status is null or a.status = :status)
              and (:branchId is null or a.branch.id = :branchId)
              and (:officerId is null or a.officer.id = :officerId)
              and (:q = '' or lower(a.applicationNo) like lower(concat('%', :q, '%'))
                           or lower(a.client.fullName) like lower(concat('%', :q, '%'))
                           or a.client.phone like concat('%', :q, '%'))
            """)
    Page<Application> search(@Param("status") ApplicationStatus status, @Param("branchId") Long branchId,
                             @Param("officerId") Long officerId, @Param("q") String q, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "branch", "officer", "decidedBy", "contract"})
    @Query("select a from Application a where a.id = :id")
    Optional<Application> findDetailed(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Application a where a.id = :id")
    Optional<Application> findForUpdate(@Param("id") Long id);

    interface StatusCount {
        ApplicationStatus getStatus();

        long getCnt();
    }

    @Query("""
            select a.status as status, count(a) as cnt from Application a
            where (:branchId is null or a.branch.id = :branchId)
            group by a.status
            """)
    List<StatusCount> countByStatus(@Param("branchId") Long branchId);
}
