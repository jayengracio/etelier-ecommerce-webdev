package app.product;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a product id from the URL or a form doesn't exist
 * @ResponseStatus makes Spring answer 404 wherever it's thrown (controller or service)
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(int id) {
        super("No product with id " + id);
    }
}
