// MeridianMart frontend JS

const API = {
    token: localStorage.getItem('mm_token'),
    userId: localStorage.getItem('mm_userId'),
    role: localStorage.getItem('mm_role'),
    signingKey: localStorage.getItem('mm_signingKey'),

    async computeBodyHash(bodyStr) {
        const encoder = new TextEncoder();
        const hashBuffer = await crypto.subtle.digest('SHA-256', encoder.encode(bodyStr));
        return Array.from(new Uint8Array(hashBuffer))
            .map(b => b.toString(16).padStart(2, '0')).join('');
    },

    async computeSignature(method, path, timestamp, nonce, bodyStr = '') {
        const signingKey = this.signingKey || '';
        const bodyHash = await this.computeBodyHash(bodyStr);
        const canonical = `${method.toUpperCase()}\n${path}\n${timestamp}\n${nonce}\n${bodyHash}`;
        const encoder = new TextEncoder();
        const rawKey = encoder.encode(signingKey);
        // crypto.subtle requires at least 1 byte; use a null byte for unauthenticated requests
        const keyMaterial = rawKey.length > 0 ? rawKey : new Uint8Array([0]);
        const key = await crypto.subtle.importKey(
            'raw', keyMaterial,
            { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']
        );
        const sig = await crypto.subtle.sign('HMAC', key, encoder.encode(canonical));
        return btoa(String.fromCharCode(...new Uint8Array(sig)));
    },

    async request(method, path, body, extraHeaders = {}) {
        const timestamp = Math.floor(Date.now() / 1000).toString();
        const nonce = crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(2);
        const bodyStr = body ? JSON.stringify(body) : '';
        const signature = await this.computeSignature(method, path, timestamp, nonce, bodyStr);

        const headers = {
            'Content-Type': 'application/json',
            'X-Timestamp': timestamp,
            'X-Nonce': nonce,
            'X-Signature': signature,
            ...extraHeaders,
        };
        if (this.token) headers['Authorization'] = 'Bearer ' + this.token;

        const response = await fetch(path, {
            method,
            headers,
            body: bodyStr || undefined,
        });

        const data = await response.json().catch(() => ({}));

        if (response.status === 401) {
            API.logout();
            return data;
        }
        return { status: response.status, data };
    },

    get(path) { return this.request('GET', path); },
    post(path, body, headers) { return this.request('POST', path, body, headers); },
    put(path, body) { return this.request('PUT', path, body); },
    delete(path) { return this.request('DELETE', path); },

    logout() {
        localStorage.removeItem('mm_token');
        localStorage.removeItem('mm_userId');
        localStorage.removeItem('mm_role');
        localStorage.removeItem('mm_signingKey');
        this.token = null;
        this.userId = null;
        this.role = null;
        this.signingKey = null;
        window.location.href = '/login';
    },

    saveAuth(token, userId, role, signingKey) {
        this.token = token;
        this.userId = userId;
        this.role = role;
        this.signingKey = signingKey || '';
        localStorage.setItem('mm_token', token);
        localStorage.setItem('mm_userId', String(userId));
        localStorage.setItem('mm_role', role);
        if (signingKey) localStorage.setItem('mm_signingKey', signingKey);
    }
};

function showAlert(container, message, type = 'success') {
    const div = document.createElement('div');
    div.className = `alert alert-${type}`;
    div.textContent = message;
    container.prepend(div);
    setTimeout(() => div.remove(), 5000);
}

function formatPrice(amount) {
    return '$' + parseFloat(amount).toFixed(2);
}

function getStatusClass(status) {
    return 'status-badge status-' + status;
}

// Load unread count in nav
async function loadUnreadCount() {
    if (!API.token) return;
    try {
        const res = await API.get('/api/notifications');
        if (res.data && res.data.data) {
            const unread = res.data.data.filter(n => !n.read).length;
            const badge = document.getElementById('notif-badge');
            if (badge) badge.textContent = unread > 0 ? unread : '';
        }
    } catch {}
}

// Login page
if (document.getElementById('login-form')) {
    if (API.token) window.location.href = '/home';

    document.getElementById('login-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const username = document.getElementById('username').value;
        const password = document.getElementById('password').value;
        const errorDiv = document.getElementById('login-error');
        const submitBtn = document.getElementById('login-btn');

        errorDiv.style.display = 'none';
        submitBtn.disabled = true;
        submitBtn.textContent = 'Signing in...';

        try {
            const timestamp = Math.floor(Date.now() / 1000).toString();
            const nonce = crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(2);
            const bodyStr = JSON.stringify({ username, password });
            // Unauthenticated requests sign with empty key (API.token is null at login time)
            const signature = await API.computeSignature('POST', '/api/auth/login', timestamp, nonce, bodyStr);
            const res = await fetch('/api/auth/login', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Timestamp': timestamp,
                    'X-Nonce': nonce,
                    'X-Signature': signature,
                },
                body: bodyStr,
            });
            const data = await res.json();

            if (res.ok && data.data) {
                API.saveAuth(data.data.token, data.data.userId, data.data.role, data.data.signingKey);
                window.location.href = '/home';
            } else {
                errorDiv.style.display = 'block';
                if (res.status === 423) {
                    document.getElementById('locked-message').style.display = 'block';
                    errorDiv.style.display = 'none';
                } else {
                    errorDiv.textContent = data.errorMessage || 'Invalid username or password.';
                }
            }
        } catch (err) {
            errorDiv.style.display = 'block';
            errorDiv.textContent = 'Connection error. Please try again.';
        } finally {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Sign In';
        }
    });
}

// Home page
if (document.getElementById('product-grid')) {
    if (!API.token) window.location.href = '/login';

    async function loadHome() {
        loadUnreadCount();
        // Load products
        const res = await API.get('/api/products?page=0&size=20');
        if (res.data && res.data.data) {
            const grid = document.getElementById('product-grid');
            grid.innerHTML = '';
            res.data.data.items.forEach(p => {
                grid.innerHTML += productCard(p);
            });
        }
        // Load recommendations
        const recRes = await API.get('/api/recommendations');
        if (recRes.data && recRes.data.data && recRes.data.data.length > 0) {
            const recGrid = document.getElementById('rec-grid');
            recGrid.innerHTML = '';
            recRes.data.data.forEach(p => {
                recGrid.innerHTML += productCard(p);
            });
            document.getElementById('rec-section').style.display = 'block';
        }
    }

    function productCard(p) {
        const warning = p.stockWarning ? `<div class="stock-warning">⚠ Low stock</div>` : '';
        return `<div class="product-card" onclick="window.location='/products/${p.id}'">
            <img src="${p.imageUrl || '/images/placeholder.svg'}" alt="${p.name}" onerror="this.src='/images/placeholder.svg'">
            <div class="product-card-body">
                <div class="product-card-category">${p.category}</div>
                <div class="product-card-title">${p.name}</div>
                <div class="product-card-price">${formatPrice(p.price)}</div>
                ${warning}
            </div>
        </div>`;
    }

    loadHome();
}

// Product detail page
if (document.getElementById('product-detail')) {
    if (!API.token) window.location.href = '/login';

    const productId = window.location.pathname.split('/').pop();

    async function loadProduct() {
        loadUnreadCount();
        const res = await API.get('/api/products/' + productId);
        if (!res.data || !res.data.data) return;
        const p = res.data.data;

        document.getElementById('product-name').textContent = p.name;
        document.getElementById('product-description').textContent = p.description || '';
        document.getElementById('product-price').textContent = formatPrice(p.price);
        document.getElementById('product-stock').textContent = 'In stock: ' + p.stockQuantity;
        document.getElementById('product-category').textContent = p.category;
        document.getElementById('product-img').src = p.imageUrl || '/images/placeholder.svg';

        if (p.stockWarning) {
            document.getElementById('stock-warning').style.display = 'flex';
        }

        // Record view event
        await API.post('/api/behavior', { productId: parseInt(productId), eventType: 'VIEW' });

        // Load recommendations
        const recRes = await API.get('/api/recommendations');
        if (recRes.data && recRes.data.data && recRes.data.data.length > 0) {
            const recGrid = document.getElementById('product-rec-grid');
            recGrid.innerHTML = '';
            recRes.data.data.slice(0, 4).forEach(r => {
                if (r.id != productId) {
                    recGrid.innerHTML += `<div class="product-card" onclick="window.location='/products/${r.id}'">
                        <img src="${r.imageUrl || '/images/placeholder.svg'}" alt="${r.name}" onerror="this.src='/images/placeholder.svg'">
                        <div class="product-card-body">
                            <div class="product-card-title">${r.name}</div>
                            <div class="product-card-price">${formatPrice(r.price)}</div>
                        </div>
                    </div>`;
                }
            });
        }
    }

    document.getElementById('add-to-cart-btn').addEventListener('click', async () => {
        const btn = document.getElementById('add-to-cart-btn');
        btn.disabled = true;
        const res = await API.post('/api/cart', { productId: parseInt(productId), quantity: 1 });
        if (res.status === 201) {
            document.getElementById('cart-success').style.display = 'block';
            await API.post('/api/behavior', { productId: parseInt(productId), eventType: 'ADD_TO_CART' });
            setTimeout(() => { document.getElementById('cart-success').style.display = 'none'; }, 3000);
        } else {
            showAlert(document.getElementById('product-alerts'),
                res.data.errorMessage || 'Failed to add to cart', 'danger');
        }
        btn.disabled = false;
    });

    document.getElementById('add-to-fav-btn').addEventListener('click', async () => {
        const res = await API.post('/api/favorites', { productId: parseInt(productId) });
        if (res.status === 201) {
            showAlert(document.getElementById('product-alerts'), 'Added to favorites!', 'success');
            await API.post('/api/behavior', { productId: parseInt(productId), eventType: 'FAVORITE' });
        } else {
            showAlert(document.getElementById('product-alerts'),
                res.data.errorMessage || 'Failed to add to favorites', 'warning');
        }
    });

    // Star rating
    let selectedScore = 0;
    document.querySelectorAll('.star').forEach(star => {
        star.addEventListener('mouseover', () => {
            const val = parseInt(star.dataset.value);
            document.querySelectorAll('.star').forEach(s => {
                s.classList.toggle('active', parseInt(s.dataset.value) <= val);
            });
        });
        star.addEventListener('mouseleave', () => {
            document.querySelectorAll('.star').forEach(s => {
                s.classList.toggle('active', parseInt(s.dataset.value) <= selectedScore);
            });
        });
        star.addEventListener('click', async () => {
            selectedScore = parseInt(star.dataset.value);
            document.querySelectorAll('.star').forEach(s => {
                s.classList.toggle('active', parseInt(s.dataset.value) <= selectedScore);
            });
            const res = await API.post('/api/ratings', { productId: parseInt(productId), score: selectedScore });
            if (res.status === 201) {
                showAlert(document.getElementById('product-alerts'), 'Rating submitted!', 'success');
                await API.post('/api/behavior', { productId: parseInt(productId), eventType: 'RATING' });
            } else {
                showAlert(document.getElementById('product-alerts'),
                    res.data.errorMessage || 'Could not submit rating', 'warning');
            }
        });
    });

    loadProduct();
}

// Cart page
if (document.getElementById('cart-container')) {
    if (!API.token) window.location.href = '/login';

    async function loadCart() {
        loadUnreadCount();
        const res = await API.get('/api/cart');
        const container = document.getElementById('cart-container');
        const totalEl = document.getElementById('cart-total');

        if (!res.data || !res.data.data || res.data.data.items.length === 0) {
            container.innerHTML = '<div class="empty-state"><p>Your cart is empty.</p></div>';
            document.getElementById('checkout-btn').style.display = 'none';
            if (totalEl) totalEl.style.display = 'none';
            return;
        }

        const { items, totalPrice } = res.data.data;
        container.innerHTML = items.map(item => `
            <div class="cart-item" id="cart-item-${item.id}">
                <img class="cart-item-img" src="${item.imageUrl || '/images/placeholder.svg'}" alt="${item.productName}" onerror="this.src='/images/placeholder.svg'">
                <div style="flex:1">
                    <div style="font-weight:600">${item.productName}</div>
                    <div style="color:var(--text-muted);font-size:0.9rem">Qty: ${item.quantity} × ${formatPrice(item.unitPrice)}</div>
                    ${item.stockWarning ? '<div class="stock-warning">⚠ Low stock</div>' : ''}
                </div>
                <div style="font-weight:700;margin-right:1rem">${formatPrice(item.subtotal)}</div>
                <button class="btn btn-danger btn-sm" onclick="removeItem(${item.id})">Remove</button>
            </div>
        `).join('');

        if (totalEl) {
            totalEl.style.display = 'block';
            totalEl.innerHTML = `<strong>Total: ${formatPrice(totalPrice)}</strong>`;
        }
        document.getElementById('checkout-btn').style.display = 'inline-flex';
    }

    window.removeItem = async (itemId) => {
        const res = await API.delete('/api/cart/' + itemId);
        if (res.status === 200) {
            loadCart();
        }
    };

    document.getElementById('checkout-btn').addEventListener('click', () => {
        window.location.href = '/checkout';
    });

    loadCart();
}

// Checkout page
if (document.getElementById('checkout-form')) {
    if (!API.token) window.location.href = '/login';

    async function loadCheckoutSummary() {
        loadUnreadCount();
        const res = await API.get('/api/cart');
        if (!res.data || !res.data.data) return;
        const { items, totalPrice } = res.data.data;
        const summaryEl = document.getElementById('order-summary');
        summaryEl.innerHTML = items.map(i =>
            `<div class="cart-item">
                <div style="flex:1"><strong>${i.productName}</strong> × ${i.quantity}</div>
                <div>${formatPrice(i.subtotal)}</div>
            </div>`
        ).join('') + `<div class="cart-total">Total: ${formatPrice(totalPrice)}</div>`;
    }

    document.getElementById('confirm-btn').addEventListener('click', async () => {
        const btn = document.getElementById('confirm-btn');
        const loading = document.getElementById('checkout-loading');
        btn.disabled = true;
        btn.textContent = 'Sending to Register...';
        loading.style.display = 'block';

        const idempotencyKey = 'order-' + Date.now() + '-' + Math.random().toString(36).substring(2);
        const res = await API.post('/api/orders', {}, { 'Idempotency-Key': idempotencyKey });

        loading.style.display = 'none';
        if (res.status === 201 && res.data.data) {
            const order = res.data.data;
            localStorage.setItem('mm_last_order', JSON.stringify(order));
            window.location.href = '/confirmation';
        } else {
            btn.disabled = false;
            btn.textContent = 'Send to Register';
            showAlert(document.getElementById('checkout-alerts'),
                res.data.errorMessage || 'Checkout failed. Please try again.', 'danger');
        }
    });

    loadCheckoutSummary();
}

// Confirmation page
if (document.getElementById('confirmation-page')) {
    const order = JSON.parse(localStorage.getItem('mm_last_order') || '{}');
    if (order.receiptNumber) {
        document.getElementById('receipt-number').textContent = order.receiptNumber;
        document.getElementById('receipt-timestamp').textContent = order.transactionTimestamp || '';
    } else {
        document.getElementById('receipt-number').textContent = 'N/A';
    }
}

// Orders page
if (document.getElementById('orders-list')) {
    if (!API.token) window.location.href = '/login';

    async function loadOrders() {
        loadUnreadCount();
        const res = await API.get('/api/orders');
        const list = document.getElementById('orders-list');
        if (!res.data || !res.data.data || res.data.data.length === 0) {
            list.innerHTML = '<div class="empty-state"><p>No orders yet.</p></div>';
            return;
        }
        list.innerHTML = `<table class="table">
            <thead><tr><th>Receipt</th><th>Date</th><th>Total</th><th>Status</th></tr></thead>
            <tbody>
            ${res.data.data.map(o => `<tr>
                <td style="font-family:monospace">${o.receiptNumber}</td>
                <td>${o.transactionTimestamp}</td>
                <td>${formatPrice(o.totalAmount)}</td>
                <td><span class="${getStatusClass(o.status)}">${o.status}</span></td>
            </tr>`).join('')}
            </tbody></table>`;
    }
    loadOrders();
}

// Notifications page
if (document.getElementById('notifications-list')) {
    if (!API.token) window.location.href = '/login';

    async function loadNotifications() {
        const res = await API.get('/api/notifications');
        const list = document.getElementById('notifications-list');
        if (!res.data || !res.data.data || res.data.data.length === 0) {
            list.innerHTML = '<div class="empty-state"><p>No notifications.</p></div>';
            return;
        }
        const unread = res.data.data.filter(n => !n.read).length;
        document.getElementById('unread-count').textContent = unread > 0 ? unread + ' unread' : '';

        list.innerHTML = res.data.data.map(n => `
            <div class="notification-item ${n.read ? '' : 'unread'}" id="notif-${n.id}">
                <div class="notification-message">${n.message}</div>
                <div style="display:flex;flex-direction:column;align-items:flex-end;gap:0.5rem">
                    <span class="notification-time">${n.createdAt ? n.createdAt.substring(0, 16).replace('T', ' ') : ''}</span>
                    ${!n.read ? `<button class="btn btn-outline btn-sm" onclick="markRead(${n.id})">Mark read</button>` : '<span style="font-size:0.75rem;color:var(--text-muted)">Read</span>'}
                </div>
            </div>
        `).join('');
    }

    window.markRead = async (id) => {
        await API.put('/api/notifications/' + id + '/read', {});
        loadNotifications();
        loadUnreadCount();
    };

    loadNotifications();
}

// Staff dashboard
if (document.getElementById('staff-dashboard')) {
    if (!API.token || (API.role !== 'STAFF' && API.role !== 'ADMIN')) {
        window.location.href = '/login';
    }

    loadUnreadCount();

    document.getElementById('receipt-search-btn').addEventListener('click', async () => {
        const receipt = document.getElementById('receipt-input').value.trim();
        if (!receipt) return;
        const res = await API.get('/api/transactions/' + receipt);
        const panel = document.getElementById('transaction-panel');
        if (res.status === 200 && res.data.data) {
            const o = res.data.data;
            panel.style.display = 'block';
            document.getElementById('tx-receipt').textContent = o.receiptNumber;
            document.getElementById('tx-time').textContent = o.transactionTimestamp;
            document.getElementById('tx-amount').textContent = formatPrice(o.totalAmount);
            document.getElementById('tx-status').className = getStatusClass(o.status);
            document.getElementById('tx-status').textContent = o.status;
            document.getElementById('tx-order-id').value = o.id;
            document.getElementById('pos-confirm-btn').style.display =
                o.status === 'PENDING_AT_REGISTER' ? 'inline-block' : 'none';

            const items = (o.items || []).map(i =>
                `<tr><td>${i.productName}</td><td>${i.quantity}</td><td>${formatPrice(i.unitPrice)}</td></tr>`
            ).join('');
            document.getElementById('tx-items').innerHTML = items;
        } else {
            panel.style.display = 'none';
            showAlert(document.getElementById('staff-alerts'),
                res.data.errorMessage || 'Transaction not found', 'danger');
        }
    });

    document.getElementById('pos-confirm-btn').addEventListener('click', async () => {
        const orderId = document.getElementById('tx-order-id').value;
        const res = await API.post('/api/orders/' + orderId + '/pos-confirm', {});
        if (res.status === 200) {
            showAlert(document.getElementById('staff-alerts'), 'Order confirmed at POS!', 'success');
            document.getElementById('receipt-search-btn').click();
        } else {
            showAlert(document.getElementById('staff-alerts'),
                res.data.errorMessage || 'POS confirm failed', 'danger');
        }
    });

    document.getElementById('refund-btn').addEventListener('click', async () => {
        const receipt = document.getElementById('receipt-input').value.trim();
        const idempotencyKey = 'refund-' + Date.now();
        const res = await API.post('/api/refunds', { receiptNumber: receipt, idempotencyKey });
        if (res.status === 200) {
            showAlert(document.getElementById('staff-alerts'), 'Refund processed successfully!', 'success');
            document.getElementById('receipt-search-btn').click();
        } else {
            showAlert(document.getElementById('staff-alerts'),
                res.data.errorMessage || 'Refund failed', 'danger');
        }
    });

    document.getElementById('pickup-btn').addEventListener('click', async () => {
        const orderId = document.getElementById('tx-order-id').value;
        const res = await API.put('/api/orders/' + orderId + '/ready-for-pickup', {});
        if (res.status === 200) {
            showAlert(document.getElementById('staff-alerts'), 'Marked as ready for pickup!', 'success');
            document.getElementById('receipt-search-btn').click();
        } else {
            showAlert(document.getElementById('staff-alerts'),
                res.data.errorMessage || 'Failed to update status', 'danger');
        }
    });
}

// Admin dashboard
if (document.getElementById('admin-dashboard')) {
    if (!API.token || API.role !== 'ADMIN') {
        window.location.href = '/login';
    }

    loadUnreadCount();

    async function loadFlags() {
        const res = await API.get('/api/feature-flags');
        if (!res.data || !res.data.data) return;
        const list = document.getElementById('flags-list');
        list.innerHTML = res.data.data.map(f => `
            <tr>
                <td>${f.flagName}</td>
                <td>${f.storeId || '-'}</td>
                <td>
                    <label class="toggle-switch">
                        <input type="checkbox" ${f.enabled ? 'checked' : ''} onchange="toggleFlag(${f.id}, this.checked)">
                        <span class="toggle-slider"></span>
                    </label>
                </td>
                <td>${f.updatedBy || '-'}</td>
                <td>${f.updatedAt ? f.updatedAt.substring(0, 16).replace('T', ' ') : '-'}</td>
            </tr>
        `).join('');
    }

    window.toggleFlag = async (id, enabled) => {
        const res = await API.put('/api/feature-flags/' + id, { isEnabled: enabled });
        if (res.status === 200) {
            showAlert(document.getElementById('admin-alerts'), 'Feature flag updated!', 'success');
            loadFlags();
        } else {
            showAlert(document.getElementById('admin-alerts'), 'Failed to update flag', 'danger');
        }
    };

    async function loadCompliance() {
        const res = await API.get('/api/compliance-reports');
        if (!res.data || !res.data.data) return;
        const report = res.data.data;
        const el = document.getElementById('compliance-data');
        const rec = report.paymentReconciliation;
        el.innerHTML = `
            <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:1rem;margin-bottom:1rem">
                <div class="card"><div style="font-size:0.8rem;color:var(--text-muted)">Successful</div><div style="font-size:1.5rem;font-weight:700;color:var(--success)">${rec.successfulTransactions}</div></div>
                <div class="card"><div style="font-size:0.8rem;color:var(--text-muted)">Refunded</div><div style="font-size:1.5rem;font-weight:700;color:var(--danger)">${rec.refundedTransactions}</div></div>
                <div class="card"><div style="font-size:0.8rem;color:var(--text-muted)">Net Revenue</div><div style="font-size:1.5rem;font-weight:700;color:var(--primary)">${formatPrice(rec.netRevenue)}</div></div>
            </div>
            <div><strong>Audit Logs (last 20):</strong>
            <table class="table" style="margin-top:0.5rem">
                <thead><tr><th>Action</th><th>Time</th></tr></thead>
                <tbody>
                ${(report.recentAuditLogs || []).map(l => `<tr><td>${l.action}</td><td style="font-size:0.8rem">${l.createdAt ? l.createdAt.substring(0,16).replace('T',' ') : ''}</td></tr>`).join('')}
                </tbody>
            </table></div>
        `;
    }

    loadFlags();
    loadCompliance();
}

// Logout handler
document.querySelectorAll('.logout-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
        await API.post('/api/auth/logout', {});
        API.logout();
    });
});

// Node.js/Jest export for unit testing — no-op in browsers where module is undefined
if (typeof module !== 'undefined') {
    module.exports = { API, formatPrice, getStatusClass };
}
