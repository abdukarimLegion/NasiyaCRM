package uz.installment.client;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientRepository extends JpaRepository<Client, Long> {

    boolean existsByPinfl(String pinfl);

    boolean existsByPinflAndIdNot(String pinfl, Long id);

    @Query("""
            select c from Client c
            where :q = ''
               or lower(c.fullName) like lower(concat('%', :q, '%'))
               or c.pinfl like concat('%', :q, '%')
               or c.phone like concat('%', :q, '%')
            """)
    Page<Client> search(@Param("q") String q, Pageable pageable);
}
