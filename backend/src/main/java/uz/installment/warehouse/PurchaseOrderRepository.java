package uz.installment.warehouse;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @Query(value = "select nextval('purchase_order_no_seq')", nativeQuery = true)
    long nextNo();

    @EntityGraph(attributePaths = {"supplier", "createdBy", "approvedBy"})
    List<PurchaseOrder> findAllByOrderByIdDesc();

    @EntityGraph(attributePaths = {"supplier", "createdBy", "approvedBy", "items", "items.product"})
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> findDetailed(@Param("id") Long id);
}
