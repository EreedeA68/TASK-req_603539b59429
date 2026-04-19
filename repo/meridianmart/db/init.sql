-- Database and schema are created by the Docker environment via MYSQL_DATABASE env var.
-- Do not hardcode CREATE DATABASE / USE here; the init script runs inside the configured database.

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'SHOPPER',
    is_locked BOOLEAN NOT NULL DEFAULT FALSE,
    failed_attempts INT NOT NULL DEFAULT 0,
    lock_time DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active DATETIME NULL,
    anonymized BOOLEAN NOT NULL DEFAULT FALSE,
    store_id VARCHAR(50) NOT NULL DEFAULT 'STORE_001',
    INDEX idx_users_email (email),
    INDEX idx_users_username (username),
    INDEX idx_users_role (role)
);

CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 0,
    category VARCHAR(100) NOT NULL,
    image_url VARCHAR(500),
    is_new_arrival BOOLEAN NOT NULL DEFAULT FALSE,
    store_id VARCHAR(50) NOT NULL DEFAULT 'STORE_001',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_products_category (category),
    INDEX idx_products_store (store_id),
    INDEX idx_products_new_arrival (is_new_arrival),
    INDEX idx_products_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    added_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    UNIQUE KEY uk_cart_user_product (user_id, product_id),
    INDEX idx_cart_user (user_id)
);

CREATE TABLE IF NOT EXISTS favorites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    UNIQUE KEY uk_favorites_user_product (user_id, product_id),
    INDEX idx_favorites_user (user_id)
);

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    receipt_number VARCHAR(50) NOT NULL UNIQUE,
    total_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    transaction_timestamp DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    store_id VARCHAR(50) NOT NULL DEFAULT 'STORE_001',
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_orders_user (user_id),
    INDEX idx_orders_store (store_id),
    INDEX idx_orders_receipt (receipt_number),
    INDEX idx_orders_status (status)
);

CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    INDEX idx_order_items_order (order_id)
);

CREATE TABLE IF NOT EXISTS ratings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    score TINYINT NOT NULL CHECK (score BETWEEN 1 AND 5),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    UNIQUE KEY uk_ratings_user_product (user_id, product_id),
    INDEX idx_ratings_product (product_id)
);

CREATE TABLE IF NOT EXISTS behavior_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    INDEX idx_behavior_user (user_id),
    INDEX idx_behavior_product (product_id),
    INDEX idx_behavior_created (created_at),
    INDEX idx_behavior_user_product (user_id, product_id)
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    message VARCHAR(500) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_notifications_user (user_id),
    INDEX idx_notifications_created (created_at)
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    action VARCHAR(100) NOT NULL,
    details TEXT,
    ip_address VARCHAR(50),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_action (action),
    INDEX idx_audit_created (created_at)
);

CREATE TABLE IF NOT EXISTS recommendations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    score DOUBLE NOT NULL,
    cached_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    UNIQUE KEY uk_rec_user_product (user_id, product_id),
    INDEX idx_rec_user (user_id),
    INDEX idx_rec_cached (cached_at)
);

CREATE TABLE IF NOT EXISTS feature_flags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    flag_name VARCHAR(100) NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    store_id VARCHAR(50),
    updated_by VARCHAR(100),
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_flag_name_store (flag_name, store_id),
    INDEX idx_flags_name (flag_name)
);

CREATE TABLE IF NOT EXISTS payment_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    method VARCHAR(50) NOT NULL DEFAULT 'WECHAT_PAY',
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    encrypted_payment_token TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    INDEX idx_payment_order (order_id),
    INDEX idx_payment_idempotency (idempotency_key)
);

CREATE TABLE IF NOT EXISTS distributed_locks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lock_name VARCHAR(255) NOT NULL UNIQUE,
    locked_by VARCHAR(255) NOT NULL,
    locked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    INDEX idx_lock_name (lock_name),
    INDEX idx_lock_expires (expires_at)
);

CREATE TABLE IF NOT EXISTS nonce_store (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nonce_value VARCHAR(255) NOT NULL UNIQUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_nonce_value (nonce_value),
    INDEX idx_nonce_created (created_at)
);

CREATE TABLE IF NOT EXISTS change_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100),
    field_name VARCHAR(100) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    changed_by VARCHAR(100),
    ip_address VARCHAR(50),
    changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_change_entity (entity_type, entity_id),
    INDEX idx_change_by (changed_by),
    INDEX idx_change_at (changed_at)
);

CREATE TABLE IF NOT EXISTS app_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value VARCHAR(500) NOT NULL,
    description VARCHAR(255),
    updated_by VARCHAR(100),
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_config_key (config_key)
);

CREATE TABLE IF NOT EXISTS disputes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    reason TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_dispute_user (user_id),
    INDEX idx_dispute_order (order_id),
    INDEX idx_dispute_status (status)
);

CREATE TABLE IF NOT EXISTS revoked_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_revoked_expires (expires_at)
);

-- =============================================
-- SEED DATA
-- =============================================

-- Passwords are bcrypt-hashed:
-- AdminTest123! -> $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy (example, will be generated properly)
-- We use bcrypt strength 10

INSERT INTO users (username, email, password_hash, role, created_at) VALUES
('admin', 'admin@example.com', '$2a$10$slYQmyNdGzTn7ZLBXBChFOC9f6kFjAqPhccnP4/6oMcQWGOaWXVfi', 'ADMIN', NOW()),
('staff', 'staff@example.com', '$2a$10$uc2VDGK.6T1xT1DYrLVOneIBzjc7oKXpW5XFjVLKY2W4GJFdVGg0a', 'STAFF', NOW()),
('shopper', 'user@example.com', '$2a$10$TRN8iVcfFBxAI./wHiYSfeJXUvMy7/JMuBjGiQkMWmpuPqaFlkVKW', 'SHOPPER', NOW()),
('guest', 'guest@example.com', '$2a$10$5XH6VW8E5J5e7V7hGJrpS.XHN3K2VqGY/Av3xQ7WxXgVlFY9Zk5Wy', 'READ_ONLY', NOW());

-- Electronics (6 products)
INSERT INTO products (name, description, price, stock_quantity, category, image_url, is_new_arrival, created_at) VALUES
('Samsung 4K Smart TV 55"', 'Crystal clear 4K display with smart features and built-in streaming', 699.99, 15, 'Electronics', '/images/tv.jpg', FALSE, NOW()),
('Apple AirPods Pro', 'Active noise cancellation wireless earbuds with spatial audio', 249.99, 1, 'Electronics', '/images/airpods.jpg', TRUE, NOW()),
('Sony PlayStation 5', 'Next-gen gaming console with ultra-high speed SSD', 499.99, 0, 'Electronics', '/images/ps5.jpg', FALSE, NOW()),
('Logitech MX Master 3 Mouse', 'Advanced wireless mouse for power users with ergonomic design', 99.99, 25, 'Electronics', '/images/mouse.jpg', FALSE, NOW()),
('iPad Air 5th Gen', 'Powerful M1 chip tablet with 10.9-inch Liquid Retina display', 599.99, 8, 'Electronics', '/images/ipad.jpg', TRUE, NOW()),
('Bose QuietComfort 45', 'Premium noise cancelling headphones with 24-hour battery', 329.99, 12, 'Electronics', '/images/headphones.jpg', FALSE, NOW());

-- Clothing (5 products)
INSERT INTO products (name, description, price, stock_quantity, category, image_url, is_new_arrival, created_at) VALUES
('Nike Air Max 270', 'Lifestyle shoes with large Air unit for all-day comfort', 150.00, 30, 'Clothing', '/images/nike.jpg', FALSE, NOW()),
('Levi''s 501 Original Jeans', 'Classic straight fit jeans in authentic stonewash', 59.99, 45, 'Clothing', '/images/jeans.jpg', FALSE, NOW()),
('North Face Fleece Jacket', 'Warm and lightweight full-zip fleece for outdoor adventures', 89.99, 20, 'Clothing', '/images/jacket.jpg', TRUE, NOW()),
('Uniqlo Ultra Light Down Vest', 'Packable down vest with warmth and style', 49.99, 35, 'Clothing', '/images/vest.jpg', FALSE, NOW()),
('Adidas Ultraboost 22', 'High-performance running shoes with responsive cushioning', 180.00, 1, 'Clothing', '/images/adidas.jpg', TRUE, NOW());

-- Food (5 products)
INSERT INTO products (name, description, price, stock_quantity, category, image_url, is_new_arrival, created_at) VALUES
('Organic Green Tea Pack (100g)', 'Premium Japanese green tea leaves, rich in antioxidants', 12.99, 100, 'Food', '/images/tea.jpg', FALSE, NOW()),
('Artisan Dark Chocolate Bar', 'Single-origin 72% cacao dark chocolate from Ecuador', 8.99, 80, 'Food', '/images/chocolate.jpg', TRUE, NOW()),
('Cold Brew Coffee Concentrate', 'Ready-to-drink cold brew, 32oz bottle, smooth and bold', 14.99, 50, 'Food', '/images/coffee.jpg', FALSE, NOW()),
('Himalayan Pink Salt (500g)', 'Mineral-rich pink salt harvested from ancient sea beds', 6.99, 120, 'Food', '/images/salt.jpg', FALSE, NOW()),
('Mixed Nuts Premium Pack', 'Premium blend of cashews, almonds, and macadamia nuts (500g)', 22.99, 60, 'Food', '/images/nuts.jpg', TRUE, NOW());

-- Home (4 products)
INSERT INTO products (name, description, price, stock_quantity, category, image_url, is_new_arrival, created_at) VALUES
('Dyson V11 Cordless Vacuum', 'Powerful cordless vacuum with intelligent suction control', 599.99, 7, 'Home', '/images/dyson.jpg', FALSE, NOW()),
('Instant Pot Duo 7-in-1', 'Multi-use pressure cooker, slow cooker, rice cooker and more', 89.99, 22, 'Home', '/images/instantpot.jpg', FALSE, NOW()),
('Philips Hue Smart Bulb Kit', 'Color-changing smart LED bulbs with app control (4-pack)', 179.99, 1, 'Home', '/images/philips.jpg', TRUE, NOW()),
('IKEA KALLAX Shelf Unit', 'Versatile cube shelf unit perfect for storage and display', 129.99, 18, 'Home', '/images/kallax.jpg', FALSE, NOW());

-- Feature flags
INSERT INTO feature_flags (flag_name, is_enabled, store_id, updated_by) VALUES
('RECOMMENDATIONS_ENABLED', TRUE, 'STORE_001', 'admin'),
('WECHAT_PAY_ENABLED', TRUE, 'STORE_001', 'admin'),
('STAFF_REFUND_ENABLED', TRUE, 'STORE_001', 'admin'),
('NEW_ARRIVALS_SECTION', TRUE, 'STORE_001', 'admin'),
('NOTIFICATION_ENABLED', TRUE, 'STORE_001', 'admin'),
('DEPOSIT_PREAUTH_ENABLED', FALSE, 'STORE_001', 'admin'),
('COMPLIANCE_REPORTS_ENABLED', TRUE, 'STORE_001', 'admin');

-- Sample orders for shopper (user id=3) - needed for recommendation testing
INSERT INTO orders (user_id, receipt_number, total_amount, status, transaction_timestamp, idempotency_key) VALUES
(3, 'RCP-20240101-001', 249.99, 'COMPLETED', DATE_SUB(NOW(), INTERVAL 10 DAY), 'idem-key-seed-001'),
(3, 'RCP-20240102-002', 59.99, 'COMPLETED', DATE_SUB(NOW(), INTERVAL 8 DAY), 'idem-key-seed-002'),
(3, 'RCP-20240103-003', 89.99, 'READY_FOR_PICKUP', DATE_SUB(NOW(), INTERVAL 3 DAY), 'idem-key-seed-003');

-- Order items
INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES
(1, 2, 1, 249.99),
(2, 8, 1, 59.99),
(3, 9, 1, 89.99);

-- Payment transactions for seeded orders
INSERT INTO payment_transactions (order_id, amount, method, status, idempotency_key) VALUES
(1, 249.99, 'WECHAT_PAY', 'SUCCESS', 'pay-idem-seed-001'),
(2, 59.99, 'WECHAT_PAY', 'SUCCESS', 'pay-idem-seed-002'),
(3, 89.99, 'WECHAT_PAY', 'SUCCESS', 'pay-idem-seed-003');

-- Behavior events for shopper (for recommendation engine)
INSERT INTO behavior_events (user_id, product_id, event_type, created_at) VALUES
(3, 1, 'VIEW', DATE_SUB(NOW(), INTERVAL 15 DAY)),
(3, 2, 'VIEW', DATE_SUB(NOW(), INTERVAL 14 DAY)),
(3, 2, 'PURCHASE', DATE_SUB(NOW(), INTERVAL 10 DAY)),
(3, 3, 'VIEW', DATE_SUB(NOW(), INTERVAL 13 DAY)),
(3, 4, 'VIEW', DATE_SUB(NOW(), INTERVAL 12 DAY)),
(3, 4, 'ADD_TO_CART', DATE_SUB(NOW(), INTERVAL 12 DAY)),
(3, 5, 'VIEW', DATE_SUB(NOW(), INTERVAL 11 DAY)),
(3, 6, 'FAVORITE', DATE_SUB(NOW(), INTERVAL 10 DAY)),
(3, 7, 'VIEW', DATE_SUB(NOW(), INTERVAL 9 DAY)),
(3, 8, 'PURCHASE', DATE_SUB(NOW(), INTERVAL 8 DAY)),
(3, 9, 'VIEW', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(3, 9, 'ADD_TO_CART', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(3, 9, 'PURCHASE', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(3, 10, 'VIEW', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(3, 11, 'VIEW', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(3, 12, 'VIEW', DATE_SUB(NOW(), INTERVAL 3 DAY)),
-- admin user also has behavior events for similarity computation
(1, 2, 'VIEW', DATE_SUB(NOW(), INTERVAL 10 DAY)),
(1, 4, 'VIEW', DATE_SUB(NOW(), INTERVAL 9 DAY)),
(1, 6, 'FAVORITE', DATE_SUB(NOW(), INTERVAL 8 DAY)),
(1, 8, 'VIEW', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(1, 10, 'VIEW', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(1, 12, 'PURCHASE', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(1, 14, 'VIEW', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(1, 16, 'ADD_TO_CART', DATE_SUB(NOW(), INTERVAL 3 DAY));

-- Ratings for shopper
INSERT INTO ratings (user_id, product_id, score, created_at) VALUES
(3, 2, 5, DATE_SUB(NOW(), INTERVAL 9 DAY)),
(3, 8, 4, DATE_SUB(NOW(), INTERVAL 7 DAY)),
(3, 9, 5, DATE_SUB(NOW(), INTERVAL 2 DAY));

-- Rating behavior events
INSERT INTO behavior_events (user_id, product_id, event_type, created_at) VALUES
(3, 2, 'RATING', DATE_SUB(NOW(), INTERVAL 9 DAY)),
(3, 8, 'RATING', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(3, 9, 'RATING', DATE_SUB(NOW(), INTERVAL 2 DAY));

-- Favorites for shopper
INSERT INTO favorites (user_id, product_id, created_at) VALUES
(3, 6, DATE_SUB(NOW(), INTERVAL 10 DAY)),
(3, 1, DATE_SUB(NOW(), INTERVAL 8 DAY));

-- Sample notifications for shopper
INSERT INTO notifications (user_id, message, is_read, created_at) VALUES
(3, 'Your order RCP-20240101-001 has been completed.', TRUE, DATE_SUB(NOW(), INTERVAL 10 DAY)),
(3, 'Your order RCP-20240102-002 has been completed.', TRUE, DATE_SUB(NOW(), INTERVAL 8 DAY)),
(3, 'Your order RCP-20240103-003 is ready for pickup!', FALSE, DATE_SUB(NOW(), INTERVAL 3 DAY));
