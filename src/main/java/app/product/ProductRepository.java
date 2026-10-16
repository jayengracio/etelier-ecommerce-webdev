package app.product;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :qty WHERE p.id = :id AND p.stock >= :qty")
    int decrementStock(int id, int qty);

    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :qty WHERE p.id = :id")
    int incrementStock(int id, int qty);

    // Shop: only products that are for sale, in a stable order
    List<Product> findByActiveTrueOrderById();

    // Dashboard: hidden products don't need restocking
    List<Product> findByActiveTrueAndStockLessThanEqualOrderByStockAscNameAsc(int threshold);
}
