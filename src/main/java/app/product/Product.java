package app.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
public class Product {

    /**
     * What the add and edit forms accept as an image: the same rule as the
     * database's
     * product_image_check, a path in this app or an http(s) URL.
     */
    public static final String IMAGE_PATTERN = "(/|https?://).*";

    public static final String IMAGE_MESSAGE = "must be a path starting with / or an http(s):// URL";

    /**
     * A blank image from a form means "no picture", which the database stores as
     * null.
     */
    public static String imageOrNull(String image) {
        return image == null || image.isBlank() ? null : image.strip();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column
    private String type;

    @Column
    private String name;

    // null means no brand
    @Column
    private String brand;

    @Column
    private int stock;

    @Column
    private String details;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    // Shown only; checkout charges the full price.
    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal discountPercentage = BigDecimal.ZERO;

    // 0 to 5 stars from DummyJSON; null: not rated
    @Column(precision = 3, scale = 2)
    private BigDecimal rating;

    @Column(nullable = false)
    private boolean active = true;

    // A path in this app ("/images/Apple.png") or a full URL; null shows a
    // placeholder.
    @Column(length = 1000)
    private String image;

    // Postgres arrays (VARCHAR[]): every picture in order, and the tags. Empty,
    // never null, for
    // products added by an admin.
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> images = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> tags = new ArrayList<>();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public BigDecimal getDiscountPercentage() {
        return discountPercentage;
    }

    public void setDiscountPercentage(BigDecimal discountPercentage) {
        this.discountPercentage = discountPercentage;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
