package app.product;

import java.util.List;
import org.springframework.data.domain.Limit;
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

    // Home page: the best-rated products you can buy right now. NULLS LAST: products added in the
    // admin have no rating, and Postgres puts NULL first in a DESC order.
    @Query("SELECT p FROM Product p WHERE p.active = true AND p.stock > 0 ORDER BY p.rating DESC NULLS LAST, p.id")
    List<Product> findFeatured(Limit limit);
}
