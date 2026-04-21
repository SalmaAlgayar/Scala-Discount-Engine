CREATE DATABASE ordersDB;
\c ordersDB;

CREATE TABLE orders (
                        timestamp TIMESTAMPTZ,
                        product_name VARCHAR(255),
                        expiry_date DATE,
                        quantity INTEGER,
                        unit_price NUMERIC(10, 2),
                        discount NUMERIC(10, 2),
                        final_price NUMERIC(10, 2),
                        channel VARCHAR(50),
                        payment_method VARCHAR(50)
);
         );
