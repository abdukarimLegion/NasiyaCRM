package uz.installment.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = "category")
    @Query("""
            select p from Product p
            where (:categoryId is null or p.category.id = :categoryId)
              and (:activeOnly = false or p.active = true)
            order by p.name
            """)
    List<Product> findFiltered(@Param("categoryId") Long categoryId, @Param("activeOnly") boolean activeOnly);

    /** Omborni kamaytirishda parallel shartnomalar bir-birini buzmasligi uchun. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findForUpdate(@Param("id") Long id);
}
