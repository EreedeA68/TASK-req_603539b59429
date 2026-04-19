/**
 * @jest-environment jsdom
 */

'use strict';

// Provide Web API stubs before the module is loaded.
// jsdom may not expose TextEncoder or crypto.subtle in all versions.
if (!global.TextEncoder) {
    const { TextEncoder, TextDecoder } = require('util');
    global.TextEncoder = TextEncoder;
    global.TextDecoder = TextDecoder;
}
if (!global.crypto) {
    global.crypto = {};
}
if (!global.crypto.subtle) {
    global.crypto.subtle = {
        digest: jest.fn(async () => new Uint8Array(32).buffer),
        importKey: jest.fn(async (_fmt, _key, _alg, _ext, _usages) => ({})),
        sign: jest.fn(async (_alg, _key, _data) => new Uint8Array(32).buffer),
    };
}
if (!global.crypto.randomUUID) {
    global.crypto.randomUUID = () => 'test-uuid-1234-5678-abcd-ef0123456789';
}

// Replace window.location so logout() can write href without jsdom throwing.
delete global.window.location;
global.window.location = { href: '/' };

const { API, formatPrice, getStatusClass } = require('../../main/resources/static/js/app.js');

// ---------------------------------------------------------------------------
// formatPrice
// ---------------------------------------------------------------------------
describe('formatPrice', () => {
    it('formats an integer as a two-decimal dollar string', () => {
        expect(formatPrice(5)).toBe('$5.00');
    });

    it('formats a float with one decimal place', () => {
        expect(formatPrice(12.5)).toBe('$12.50');
    });

    it('formats a float with two decimal places', () => {
        expect(formatPrice(99.99)).toBe('$99.99');
    });

    it('formats a string number', () => {
        expect(formatPrice('29.9')).toBe('$29.90');
    });

    it('formats zero', () => {
        expect(formatPrice(0)).toBe('$0.00');
    });
});

// ---------------------------------------------------------------------------
// getStatusClass
// ---------------------------------------------------------------------------
describe('getStatusClass', () => {
    it('returns correct class for PENDING', () => {
        expect(getStatusClass('PENDING')).toBe('status-badge status-PENDING');
    });

    it('returns correct class for COMPLETED', () => {
        expect(getStatusClass('COMPLETED')).toBe('status-badge status-COMPLETED');
    });

    it('returns correct class for READY_FOR_PICKUP', () => {
        expect(getStatusClass('READY_FOR_PICKUP')).toBe('status-badge status-READY_FOR_PICKUP');
    });

    it('returns correct class for PENDING_AT_REGISTER', () => {
        expect(getStatusClass('PENDING_AT_REGISTER')).toBe('status-badge status-PENDING_AT_REGISTER');
    });
});

// ---------------------------------------------------------------------------
// API.saveAuth
// ---------------------------------------------------------------------------
describe('API.saveAuth', () => {
    beforeEach(() => {
        localStorage.clear();
        API.token = null;
        API.userId = null;
        API.role = null;
        API.signingKey = null;
    });

    it('persists all four fields to localStorage', () => {
        API.saveAuth('tok-xyz', 42, 'SHOPPER', 'signing-key-abc');
        expect(localStorage.getItem('mm_token')).toBe('tok-xyz');
        expect(localStorage.getItem('mm_userId')).toBe('42');
        expect(localStorage.getItem('mm_role')).toBe('SHOPPER');
        expect(localStorage.getItem('mm_signingKey')).toBe('signing-key-abc');
    });

    it('sets matching instance properties on the API object', () => {
        API.saveAuth('mytoken', 7, 'ADMIN', 'sk');
        expect(API.token).toBe('mytoken');
        expect(API.userId).toBe(7);
        expect(API.role).toBe('ADMIN');
        expect(API.signingKey).toBe('sk');
    });

    it('defaults signingKey to empty string when argument is omitted', () => {
        API.saveAuth('t', 1, 'SHOPPER');
        expect(API.signingKey).toBe('');
        // localStorage should NOT contain the key when signingKey is falsy
        expect(localStorage.getItem('mm_signingKey')).toBeNull();
    });

    it('overwrites previous auth state on re-login', () => {
        API.saveAuth('old-tok', 1, 'SHOPPER', 'old-sk');
        API.saveAuth('new-tok', 2, 'ADMIN', 'new-sk');
        expect(API.token).toBe('new-tok');
        expect(localStorage.getItem('mm_token')).toBe('new-tok');
    });
});

// ---------------------------------------------------------------------------
// API.logout
// ---------------------------------------------------------------------------
describe('API.logout', () => {
    beforeEach(() => {
        API.saveAuth('active-tok', 99, 'SHOPPER', 'active-sk');
        window.location.href = '/home';
    });

    it('removes mm_token from localStorage', () => {
        API.logout();
        expect(localStorage.getItem('mm_token')).toBeNull();
    });

    it('removes mm_userId from localStorage', () => {
        API.logout();
        expect(localStorage.getItem('mm_userId')).toBeNull();
    });

    it('removes mm_role from localStorage', () => {
        API.logout();
        expect(localStorage.getItem('mm_role')).toBeNull();
    });

    it('removes mm_signingKey from localStorage', () => {
        API.logout();
        expect(localStorage.getItem('mm_signingKey')).toBeNull();
    });

    it('nulls out API.token', () => {
        API.logout();
        expect(API.token).toBeNull();
    });

    it('nulls out API.signingKey', () => {
        API.logout();
        expect(API.signingKey).toBeNull();
    });

    it('redirects to /login', () => {
        API.logout();
        expect(window.location.href).toBe('/login');
    });
});

// ---------------------------------------------------------------------------
// API.computeSignature — verifies it calls crypto.subtle correctly
// ---------------------------------------------------------------------------
describe('API.computeSignature', () => {
    it('invokes crypto.subtle.sign and returns a base64 string', async () => {
        const sig = await API.computeSignature('POST', '/api/auth/login', '1700000000', 'nonce-abc', '{}');
        expect(typeof sig).toBe('string');
        expect(sig.length).toBeGreaterThan(0);
        expect(global.crypto.subtle.sign).toHaveBeenCalled();
    });
});
