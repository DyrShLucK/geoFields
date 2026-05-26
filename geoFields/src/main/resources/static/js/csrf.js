/**
 * Утилиты для работы со Spring Security CSRF (с использованием CookieCsrfTokenRepository).
 */

/**
 * Получить значение токена из XSRF Cookie.
 */
function readXsrfToken() {
    const value = "; " + document.cookie;
    const parts = value.split("; XSRF-TOKEN=");
    if (parts.length === 2) return decodeURIComponent(parts.pop().split(";").shift());
    return "";
}

/**
 * Обогатить объект заголовков заголовком защиты CSRF.
 */
function csrfHeaders(extraHeaders) {
    const headers = Object.assign({}, extraHeaders || {});
    const token = readXsrfToken();
    if (token) {
        headers['X-XSRF-TOKEN'] = token;
    }
    return headers;
}