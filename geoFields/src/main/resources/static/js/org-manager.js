/**
 * Страница «Менеджер организации»: заявки на регистрацию и одноразовые ссылки-приглашения.
 * Всё общение с бэкендом — JSON по /api/org/manager/*, сессия через cookie (same-origin).
 */
(function () {
    const API = '/api/org/manager';
    const fetchOpts = { credentials: 'same-origin' };

    /**
     * Заголовки для POST: Content-Type + CSRF.
     * Сначала берём token/header из последнего summary (надёжнее), иначе — из cookie через csrf.js.
     */
    function postHeaders(csrfFromSummary) {
        const h = csrfHeaders({ 'Content-Type': 'application/json' });
        const headerName = csrfFromSummary && csrfFromSummary.headerName ? csrfFromSummary.headerName : 'X-XSRF-TOKEN';
        const token = (csrfFromSummary && csrfFromSummary.token) ? csrfFromSummary.token : readXsrfToken();
        if (token) {
            h[headerName] = token;
        }
        return h;
    }

    /** Последний ответ summary — чтобы POST сразу после загрузки не слались без токена. */
    let lastCsrf = null;

    /** Сообщение об успехе/ошибке над контентом страницы. */
    function showBanner(text, isError) {
        const el = document.getElementById('banner');
        if (!text) {
            el.innerHTML = '';
            return;
        }
        el.innerHTML = '<div class="msg ' + (isError ? 'err' : 'ok') + '">' + escapeHtml(text) + '</div>';
    }

    /** Чтобы в баннер не попал сырой HTML из ответа сервера. */
    function escapeHtml(s) {
        if (s == null) return '';
        return String(s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    /** Тянет сводку, перерисовывает таблицу заявок и список приглашений, вешает обработчики на кнопки. */
    async function loadSummary() {
        showBanner('', false);
        const res = await fetch(API + '/summary', fetchOpts);
        if (!res.ok) {
            document.getElementById('pending-wrap').innerHTML = '<p>Не удалось загрузить данные (' + res.status + ').</p>';
            return;
        }
        const data = await res.json();
        lastCsrf = data.csrf || null;
        document.getElementById('org-name').textContent = (data.organizationName && String(data.organizationName).trim() !== '')
            ? data.organizationName
            : ('#' + String(data.organizationId));
        var ul = document.getElementById('user-label');
        if (ul) {
            ul.textContent = 'Вы: ' + (data.currentUserFullName || data.currentLogin || '');
        }

        const pw = document.getElementById('pending-wrap');
        if (!data.pending || data.pending.length === 0) {
            pw.innerHTML = '<p>Нет ожидающих заявок.</p>';
        } else {
            let rows = data.pending.map(function (p) {
                return '<tr><td>' + escapeHtml(p.lastName) + '</td><td>' + escapeHtml(p.firstName) + '</td><td>' + escapeHtml(p.middleName) + '</td>' +
                    '<td>' + escapeHtml(p.login) + '</td><td>' + escapeHtml(p.email) + '</td><td>' + escapeHtml(p.createdAt) + '</td><td>' +
                    '<button type="button" data-act="approve" data-id="' + p.id + '">Принять</button> ' +
                    '<button type="button" class="danger" data-act="reject" data-id="' + p.id + '">Отклонить</button></td></tr>';
            }).join('');
            pw.innerHTML = '<table><thead><tr><th>Фамилия</th><th>Имя</th><th>Отчество</th><th>Логин</th><th>Email</th><th>Дата</th><th></th></tr></thead><tbody>' + rows + '</tbody></table>';
        }

        const iw = document.getElementById('invites-wrap');
        if (!data.invites || data.invites.length === 0) {
            iw.innerHTML = '<p>Нет активных приглашений.</p>';
        } else {
            let items = data.invites.map(function (inv) {
                const link = inv.registerUrl || ('/register?ref=' + encodeURIComponent(inv.token));
                return '<li class="token">' + escapeHtml(inv.token) +
                    '<br><a href="' + escapeHtml(link) + '">' + escapeHtml(link) + '</a> ' +
                    '<button type="button" data-act="revoke" data-token="' + escapeHtml(inv.token) + '">Отозвать</button></li>';
            }).join('');
            iw.innerHTML = '<ul style="margin:0;padding-left:20px;">' + items + '</ul>';
        }

        document.querySelectorAll('#pending-wrap button[data-act]').forEach(function (btn) {
            btn.addEventListener('click', onPendingAction);
        });
        document.querySelectorAll('#invites-wrap button[data-act]').forEach(function (btn) {
            btn.addEventListener('click', onRevoke);
        });
    }

    /** Универсальный POST: показывает результат, затем обновляет таблицы с актуальным CSRF. */
    async function apiPost(path, body) {
        const res = await fetch(API + path, {
            method: 'POST',
            credentials: 'same-origin',
            headers: postHeaders(lastCsrf),
            body: JSON.stringify(body),
        });
        const text = await res.text();
        let payload = null;
        try {
            payload = text ? JSON.parse(text) : null;
        } catch (_) { /* empty */ }
        if (!res.ok) {
            showBanner('Ошибка ' + res.status + (payload && payload.message ? ': ' + payload.message : ''), true);
            return;
        }
        if (payload && payload.message) {
            showBanner(payload.message, false);
        }
        await loadSummary();
    }

    /** Принять или отклонить заявку по data-id строки. */
    function onPendingAction(ev) {
        const btn = ev.currentTarget;
        const id = parseInt(btn.getAttribute('data-id'), 10);
        const act = btn.getAttribute('data-act');
        if (act === 'approve') {
            apiPost('/approve', { userId: id });
        } else if (act === 'reject') {
            apiPost('/reject', { userId: id });
        }
    }

    /** Отозвать приглашение до того, как по ссылке зарегистрируются. */
    function onRevoke(ev) {
        const btn = ev.currentTarget;
        const token = btn.getAttribute('data-token');
        apiPost('/revoke-invite', { token: token });
    }

    /** Новая ссылка: сначала обновляем summary (чтобы CSRF был свежий), потом POST /invite. */
    document.getElementById('btn-new-invite').addEventListener('click', async function () {
        await loadSummary();
        const res = await fetch(API + '/invite', {
            method: 'POST',
            credentials: 'same-origin',
            headers: postHeaders(lastCsrf),
            body: '{}',
        });
        const payload = await res.json().catch(function () { return null; });
        if (!res.ok) {
            showBanner('Не удалось создать приглашение (' + res.status + ')', true);
            return;
        }
        if (payload && payload.message) {
            showBanner(payload.message + ' Ссылка: ' + (payload.registerUrl || ''), false);
        }
        await loadSummary();
    });

    loadSummary().catch(function () {
        showBanner('Ошибка сети при загрузке страницы.', true);
    });
})();
