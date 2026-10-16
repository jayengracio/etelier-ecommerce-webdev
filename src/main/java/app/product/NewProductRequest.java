package app.product;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * What an admin may send to POST /product/add. No id field, so a request can't
 * overwrite an existing product. Limits match the product table
 */
public record NewProductRequest(
        @NotBlank @Size(max = 50) String type,
        @NotBlank @Size(max = 255) String name,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @Size(max = 1000) String details,
        @NotNull @PositiveOrZero Integer stock) {

    public Product toProduct() {
        Product product = new Product();
        product.setType(type.trim());
        product.setName(name.trim());
        product.setPrice(price);
        product.setDetails(details);
        product.setStock(stock);
        return product;
    }
}
