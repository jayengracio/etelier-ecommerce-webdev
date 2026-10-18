package app.product;

import static app.TestData.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.TestContainerConfiguration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(TestContainerConfiguration.class)
class ReviewRepositoryTest {

    // Postgres keeps microseconds; Instant.now() can have more digits.
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    ReviewRepository reviews;

    @Autowired
    TestEntityManager em;

    private Review save(Product product, String comment, Instant createdAt) {
        Review review = new Review(product, 4, comment, "Test Reviewer");
        review.setCreatedAt(createdAt);
        return em.persistFlushFind(review);
    }

    @Test
    void aProductsReviewsComeNewestFirst() {
        Product product = em.persist(product("Test Widget", 5));
        save(product, "Old", NOW.minus(2, ChronoUnit.DAYS));
        save(product, "New", NOW);
        save(product, "Middle", NOW.minus(1, ChronoUnit.DAYS));

        assertThat(reviews.findByProductIdOrderByCreatedAtDescIdDesc(product.getId()))
                .extracting(Review::getComment)
                .containsExactly("New", "Middle", "Old");
    }

    @Test
    void reviewsOfOtherProductsAreLeftOut() {
        Product mine = em.persist(product("Test Mine", 5));
        Product other = em.persist(product("Test Other", 5));
        save(mine, "Mine", NOW);
        save(other, "Other", NOW);

        assertThat(reviews.findByProductIdOrderByCreatedAtDescIdDesc(mine.getId()))
                .extracting(Review::getComment)
                .containsExactly("Mine");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void aRatingMustBeOneToFiveStars(int rating) {
        Product product = em.persist(product("Test Widget", 5));

        assertThatThrownBy(() -> reviews.saveAndFlush(new Review(product, rating, "Hmm", "Test Reviewer")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("reviews_rating_check");
    }
}
