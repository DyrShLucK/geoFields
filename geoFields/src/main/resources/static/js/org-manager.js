/**
 * Панель «Менеджер»: Регистрационные заявки и одноразовые приглашения.
 */
(function () {
    const API = '/api/org/manager';
    const fetchOpts = { credentials: 'same-origin' };
    let lastCsrf = null;

    function postHeaders(csrfFromSummary) {
        const headers = csrfHeaders({ 'Content-Type': 'application/json' });
        const headerName = csrfFromSummary && csrfFromSummary.headerName ? csrfFromSummary.headerName : 'X-XSRF-TOKEN';
        const token = (csrfFromSummary && csrfFromSummary.token) ? csrfFromSummary.token : readXsrfToken();
        if (token) {
            headers[headerName] = token;
        }
        return headers;
    }

    function showBanner(text, isError) {
        const el = document.getElementById('banner');
        if (!text) {
            el.innerHTML = '';
            return;
        }
        el.innerHTML = '<div class="msg ' + (isError ? 'err' : 'ok') + '">' + escapeHtml(text) + '</div>';
    }

    function escapeHtml(s) {
        if (s == null) return '';
        return String(s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    async function loadSummary() {
        showBanner('', false);
        const res = await fetch(API + '/summary', fetchOpts);
        if (!res.ok) {
            document.getElementById('pending-wrap').innerHTML = '<p class="muted">Ошибка получения данных.</p>';
            return;
        }
        const data = await res.json();
        lastCsrf = data.csrf || null;
        document.getElementById('org-name').textContent = data.organizationName || ('#' + data.organizationId);
        
        const ul = document.getElementById('user-label');
        if (ul) {
            ul.textContent = 'Вы: ' + (data.currentUserFullName || data.currentLogin || '');
        }

        // Рендеринг заявок
        const pw = document.getElementById('pending-wrap');
        if (!data.pending || data.pending.length === 0) {
            pw.innerHTML = '<p class="muted">Новых заявок пока нет.</p>';
        } else {
            let rows = data.pending.map(function (p) {
                return '<tr><td>' + escapeHtml(p.lastName) + '</td><td>' + escapeHtml(p.firstName) + '</td><td>' + escapeHtml(p.login) + '</td><td>' + escapeHtml(p.email) + '</td>' +
                    '<td><button type="button" class="btn primary mr-2" data-act="approve" data-id="' + p.id + '">Принять</button>' +
                    '<button type="button" class="btn danger" data-act="reject" data-id="' + p.id + '">Отклонить</button></td></tr>';
            }).join('');
            pw.innerHTML = '<table><thead><tr><th>Фамилия</th><th>Имя</th><th>Логин</th><th>Email</th><th>Решения</th></tr></thead><tbody>' + rows + '</tbody></table>';
        }

        // Рендеринг инвайтов
        const iw = document.getElementById('invites-wrap');
        if (!data.invites || data.invites.length === 0) {
            iw.innerHTML = '<p class="muted">Активные ссылки отсутствуют.</p>';
        } else {
            let items = data.invites.map(function (inv) {
                const link = inv.registerUrl || ('/register?ref=' + encodeURIComponent(inv.token));
                return '<div class="token">Токен: <strong>' + escapeHtml(inv.token) + '</strong><br>' +
                    '<a href="' + escapeHtml(link) + '" target="_blank">' + escapeHtml(link) + '</a><br>' +
                    '<button type="button" class="btn danger mt-2" data-act="revoke" data-token="' + escapeHtml(inv.token) + '">Деактивировать ссылку</button></div>';
            }).join('');
            iw.innerHTML = items;
        }

        document.querySelectorAll('button[data-act]').forEach(function (btn) {
            btn.addEventListener('click', onBtnAction);
        });
    }

    async function apiPost(path, body) {
        const res = await fetch(API + path, {
            method: 'POST',
            credentials: 'same-origin',
            headers: postHeaders(lastCsrf),
            body: JSON.stringify(body),
        });
        const text = await res.text();
        let payload = null;
        try { payload = text ? JSON.parse(text) : null; } catch (_) {}

        if (!res.ok) {
            showBanner('Сбой выполнения: ' + (payload ? payload.message : res.status), true);
            return;
        }
        showBanner('Успешно.', false);
        await loadSummary();
    }

    function onBtnAction(ev) {
        const btn = ev.currentTarget;
        const act = btn.getAttribute('data-act');
        if (act === 'approve') {
            apiPost('/approve', { userId: parseInt(btn.getAttribute('data-id'), 10) });
        } else if (act === 'reject') {
            apiPost('/reject', { userId: parseInt(btn.getAttribute('data-id'), 10) });
        } else if (act === 'revoke') {
            apiPost('/revoke-invite', { token: btn.getAttribute('data-token') });
        }
    }

    document.getElementById('btn-new-invite').addEventListener('click', async function () {
        await loadSummary();
        const res = await fetch(API + '/invite', {
            method: 'POST',
            credentials: 'same-origin',
            headers: postHeaders(lastCsrf),
            body: '{}',
        });
        if (!res.ok) {
            showBanner('Не удалось сгенерировать токен.', true);
            return;
        }
        showBanner('Приглашение успешно создано.', false);
        await loadSummary();
    });

    loadSummary().catch(function () {
        showBanner('Сбой инициализации страницы.', true);
    });
})();