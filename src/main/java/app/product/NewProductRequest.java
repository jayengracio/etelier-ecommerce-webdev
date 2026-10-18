package app.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * What an admin may send to POST /product/add. No id field, so a request can't
 * overwrite an existing product. Limits match the product table
 */
public record NewProductRequest(
        @NotBlank @Size(max = 50) String type,
        @NotBlank @Size(max = 255) String name,

        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2)
        BigDecimal price,

        @NotBlank @Size(max = 1000) String details,
        @NotNull @PositiveOrZero Integer stock,

        @Size(max = 1000) @Pattern(regexp = Product.IMAGE_PATTERN, message = Product.IMAGE_MESSAGE)
        String image) {

    // Runs before validation, so "" and "  " count as no picture instead of failing the pattern.
    public NewProductRequest {
        image = Product.imageOrNull(image);
    }

    public Product toProduct() {
        Product product = new Product();
        product.setType(type.trim());
        product.setName(name.trim());
        product.setPrice(price);
        product.setDetails(details);
        product.setStock(stock);
        product.setImage(image);
        return product;
    }
}
