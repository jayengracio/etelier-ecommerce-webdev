package app.product;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Integer> {

    // Details page: newest first; the id breaks ties (DummyJSON's reviews share a few dates).
    List<Review> findByProductIdOrderByCreatedAtDescIdDesc(int productId);
}
