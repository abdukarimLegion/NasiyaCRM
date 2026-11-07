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
            where :q = ''
               or lower(c.fullName) like lower(concat('%', :q, '%'))
               or c.pinfl like concat('%', :q, '%')
               or c.phone like concat('%', :q, '%')
            """)
    Page<Client> search(@Param("q") String q, Pageable pageable);
}
