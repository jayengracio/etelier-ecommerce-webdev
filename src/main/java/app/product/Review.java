package app.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A review from DummyJSON's sample data. The reviewer is a name, not an account here.
 * Product has no list of its reviews: ReviewRepository loads them when a page needs them,
 * so loading a product never pulls in its reviews.
 */
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    // 1 to 5 stars
    @Column(nullable = false)
    private int rating;

    @Column(nullable = false)
    private String comment;

    @Column(nullable = false)
    private String reviewerName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Review() {}

    public Review(Product product, int rating, String comment, String reviewerName) {
        this.product = product;
        this.rating = rating;
        this.comment = comment;
        this.reviewerName = reviewerName;
    }

    public int getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
