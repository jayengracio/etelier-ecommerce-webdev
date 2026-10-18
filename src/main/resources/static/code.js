function setCsrfHeader(xhr) {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (token && header) {
        xhr.setRequestHeader(header.content, token.content);
    }
}

function addProduct(event) {
    event.preventDefault();
    var form = event.target;
    var record = {
        type: document.getElementById("type").value,
        name: document.getElementById("name").value,
        price: Number(document.getElementById("price").value),
        details: document.getElementById("details").value,
        stock: Number(document.getElementById("stock").value),
    };

    var xhr = new XMLHttpRequest();
    xhr.addEventListener("load", function () {
        if (xhr.status === 200) {
            alert("Product added!");
            form.reset();
        } else {
            alert("Could not add product (error " + xhr.status + ")");
        }
    });
    xhr.open("POST", "/product/add");
    xhr.setRequestHeader('Content-type', 'application/json');
    setCsrfHeader(xhr);
    xhr.send(JSON.stringify(record));
}

function removeProduct(id) {
    var xhr = new XMLHttpRequest();
    xhr.addEventListener("load", removeProductFromCart);
    xhr.open("POST", "/cart/remove?id=" + id);
    setCsrfHeader(xhr);
    xhr.send();
}

function removeProductFromCart() {
    if (this.status !== 200) {
        alert("Could not remove item (error " + this.status + ")");
        return;
    }
    // Reload so the server recomputes totals, stock warnings and the empty cart page.
    location.reload();
}

// The search boxes on both products pages. Every product (a card or a table row) carries
// data-type and data-name; it stays visible only when it matches both boxes.
function filterProducts() {
    const type = document.getElementById("searchType").value.toUpperCase();
    const name = document.getElementById("searchName").value.toUpperCase();
    document.querySelectorAll("[data-type]").forEach(function (product) {
        product.hidden = !product.dataset.type.toUpperCase().includes(type)
            || !product.dataset.name.toUpperCase().includes(name);
    });
}

(function () {
    'use strict';
    window.addEventListener('load', function () {
        // Get the forms we want to add validation styles to
        var forms = document.getElementsByClassName('needs-validation');
        // Loop over them and prevent submission
        var validation = Array.prototype.filter.call(forms, function (form) {
            form.addEventListener('submit', function (event) {
                if (form.checkValidity() === false) {
                    event.preventDefault();
                    event.stopPropagation();
                }
                form.classList.add('was-validated');
            }, false);
        });
    }, false);
})();

// ---- Add to cart without leaving the page ----
// Forms marked data-add-to-cart are sent with fetch instead of loading a new page. The server
// answers JSON (MainController.addToCartFromScript) and the toast in fragments/cart-toast.html
// shows how it went. Without the toast (visitors) or without JavaScript, the form posts normally.

const TOAST_HIDE_MS = 4000;
let toastTimer;
let lastAdd = 0;

document.addEventListener("submit", function (event) {
    const form = event.target;
    const toast = document.getElementById("cart-toast");
    if (!form.matches("form[data-add-to-cart]") || !toast) {
        return;
    }
    event.preventDefault();
    addToCart(form, toast);
});

document.addEventListener("click", function (event) {
    if (event.target.closest("#cart-toast-close")) {
        hideToast();
    }
});

async function addToCart(form, toast) {
    const thisAdd = ++lastAdd;
    const button = form.querySelector("button");
    button.disabled = true; // no double adds while the first one runs

    let state = "error";
    let message = "Couldn't add this to your cart. Please try again.";
    try {
        const response = await fetch(form.action, {
            method: "POST",
            // the form's fields, including the _csrf token Thymeleaf adds, as a normal form post
            body: new URLSearchParams(new FormData(form)),
            // X-Requested-With picks the JSON handler; Accept makes Spring's errors JSON too
            headers: { "X-Requested-With": "XMLHttpRequest", "Accept": "application/json" },
        });
        if (response.redirected) {
            // Logged out in the meantime: fetch followed the redirect to the login page.
            location.href = response.url;
            return;
        }
        if (response.ok || response.status === 409) {
            showCartCount((await response.json()).cartCount);
        }
        if (response.ok) {
            state = "added";
            message = "Added to your cart";
        } else if (response.status === 409) {
            message = "Sorry, there's no more in stock";
        } else if (response.status === 404) {
            message = "This product is no longer sold";
        }
    } catch (error) {
        // Network error: keep the "try again" message.
    } finally {
        button.disabled = false;
    }
    if (thisAdd === lastAdd) { // otherwise a newer click owns the toast now
        showToast(toast, form.dataset.name, form.dataset.image, state, message);
    }
}

// state is "added" or "error": the icon with that data-state shows, the other hides.
function showToast(toast, name, image, state, message) {
    document.getElementById("cart-toast-name").textContent = name;
    const img = document.getElementById("cart-toast-image");
    const noImage = document.getElementById("cart-toast-no-image");
    // No data-image (the product has no picture): straight to the placeholder.
    img.hidden = !image;
    noImage.hidden = Boolean(image);
    img.onerror = function () { // the picture didn't load
        img.hidden = true;
        noImage.hidden = false;
    };
    if (image) {
        img.src = image;
    }
    toast.querySelectorAll("[data-state]").forEach(function (icon) {
        icon.hidden = icon.dataset.state !== state;
    });
    document.getElementById("cart-toast-message").textContent = message;
    toast.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(hideToast, TOAST_HIDE_MS);
}

function hideToast() {
    clearTimeout(toastTimer);
    document.getElementById("cart-toast").hidden = true;
}

// Same wording as the label in fragments/navbar.html.
function showCartCount(count) {
    const badge = document.getElementById("cart-count");
    if (!badge) {
        return;
    }
    badge.textContent = count;
    badge.hidden = count === 0;
    document.getElementById("cart-count-label").textContent = count === 1 ? ", 1 item" : ", " + count + " items";
}
