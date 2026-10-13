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
    var id = this.responseText;
    var table = document.getElementById("cartTable");
    var row = document.getElementById("row_" + id);
    table.deleteRow(row.rowIndex);
}

function filterByName() {
    var input, filter, table, tr, td, i, txtValue;
    input = document.getElementById("searchName");
    filter = input.value.toUpperCase();
    table = document.getElementById("productTable");
    tr = table.getElementsByTagName("tr");

    for (i = 0; i < tr.length; i++) {
        td = tr[i].getElementsByTagName("td")[1];
        if (td) {
            txtValue = td.textContent || td.innerText;
            if (txtValue.toUpperCase().indexOf(filter) > -1) {
                tr[i].style.display = "";
            } else {
                tr[i].style.display = "none";
            }
        }
    }
}

function filterByType() {
    var input, filter, table, tr, td, i, txtValue;
    input = document.getElementById("searchType");
    filter = input.value.toUpperCase();
    table = document.getElementById("productTable");
    tr = table.getElementsByTagName("tr");

    for (i = 0; i < tr.length; i++) {
        td = tr[i].getElementsByTagName("td")[0];
        if (td) {
            txtValue = td.textContent || td.innerText;
            if (txtValue.toUpperCase().indexOf(filter) > -1) {
                tr[i].style.display = "";
            } else {
                tr[i].style.display = "none";
            }
        }
    }
}

function hideProduct(id) {
    var xhr = new XMLHttpRequest();
    xhr.addEventListener("load", hideProductFromList);
    xhr.open("GET", "/hide/product?id=" + id);
    xhr.send();
}

function hideProductFromList() {
    var id = this.responseText;
    var table = document.getElementById("productTable");
    var row = document.getElementById("row_" + id);
    table.deleteRow(row.rowIndex);
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

