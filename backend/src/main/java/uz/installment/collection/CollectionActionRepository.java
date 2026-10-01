package uz.installment.collection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollectionActionRepository extends JpaRepository<CollectionAction, Long> {

    List<CollectionAction> findByContractIdOrderByIdDesc(Long contractId);
}
