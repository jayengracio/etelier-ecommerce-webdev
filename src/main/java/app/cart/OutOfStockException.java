package app.cart;

public class OutOfStockException extends RuntimeException {

    public OutOfStockException(String productName) {
        super("Not enough stock for " + productName);
    }
}
