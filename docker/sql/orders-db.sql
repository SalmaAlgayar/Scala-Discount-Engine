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

INSERT INTO orders (
    timestamp,
    product_name,
    expiry_date,
    quantity,
    unit_price,
    discount,
    final_price,
    channel,
    payment_method
) VALUES (
             '2023-04-18T18:18:40Z',
             'Wine - White Pinot Grigio',
             '2023-06-10',
             6,
             122.47,
             50,
             90.9,
             'Store',
             'Visa'
         );