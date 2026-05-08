/**
 * Утилиты CSRF для fetch POST к Spring Security с CookieCsrfTokenRepository.
 * Сервер кладёт токен в cookie XSRF-TOKEN; в заголовке POST нужно передать X-XSRF-TOKEN.
 */

/** Достаёт значение токена из cookie (браузер сам шлёт cookie, заголовок добавляем вручную). */
function readXsrfToken() {
    const m = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
    return m ? decodeURIComponent(m[1]) : '';
}

/** Клонирует extra и при наличии токена добавляет заголовок для проверки CSRF на бэкенде. */
function csrfHeaders(extra) {
    const h = Object.assign({}, extra || {});
    const t = readXsrfToken();
    if (t) {
        h['X-XSRF-TOKEN'] = t;
    }
    return h;
}
