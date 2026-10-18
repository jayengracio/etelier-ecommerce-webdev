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
 * The fields edit.html may change.
 * The id comes separetly as @RequestParams.
 */
public record ProductEditForm(
        @NotBlank @Size(max = 1000) String details,

        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2)
        BigDecimal price,

        @NotNull @PositiveOrZero Integer stock,

        @Size(max = 1000) @Pattern(regexp = Product.IMAGE_PATTERN, message = Product.IMAGE_MESSAGE)
        String image) {

    // Runs before validation: clearing the field removes the picture.
    public ProductEditForm {
        image = Product.imageOrNull(image);
    }
}
