/**
 * Админка организации: список пользователей, смена роли, удаление.
 * API: /api/org/admin/*, только для пользователей с ролью ORG_ADMIN (иначе 403).
 */
(function () {
    const API = '/api/org/admin';
    const fetchOpts = { credentials: 'same-origin' };

    /** Подписи в выпадающем списке ролей (значения должны совпадать с enum на бэкенде). */
    const ROLE_OPTIONS = [
        { value: 'USER', label: 'Пользователь' },
        { value: 'AGRONOMIST', label: 'Агроном' },
        { value: 'ORG_MANAGER', label: 'Менеджер организации' },
        { value: 'ORG_ADMIN', label: 'Администратор организации' },
    ];

    /** POST с JSON + CSRF (как на странице менеджера). */
    function postHeaders(csrfFromSummary) {
        const h = csrfHeaders({ 'Content-Type': 'application/json' });
        const headerName = csrfFromSummary && csrfFromSummary.headerName ? csrfFromSummary.headerName : 'X-XSRF-TOKEN';
        const token = (csrfFromSummary && csrfFromSummary.token) ? csrfFromSummary.token : readXsrfToken();
        if (token) {
            h[headerName] = token;
        }
        return h;
    }

    let lastCsrf = null;

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

    /** HTML <select> по одному пользователю; для текущего пользователя — disabled (роль себе не меняем с UI). */
    function roleSelectHtml(currentRole, userId, disabled) {
        let opts = ROLE_OPTIONS.map(function (o) {
            const sel = o.value === currentRole ? ' selected' : '';
            return '<option value="' + escapeHtml(o.value) + '"' + sel + '>' + escapeHtml(o.label) + '</option>';
        }).join('');
        return '<select data-user-id="' + userId + '" data-kind="role"' + (disabled ? ' disabled' : '') + '>' + opts + '</select>';
    }

    /** Загрузка таблицы пользователей и подписи «Вы: …». */
    async function loadSummary() {
        showBanner('', false);
        const res = await fetch(API + '/summary', fetchOpts);
        if (!res.ok) {
            document.getElementById('members-wrap').innerHTML = '<p>Не удалось загрузить данные (' + res.status + ').</p>';
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

        const mw = document.getElementById('members-wrap');
        if (!data.members || data.members.length === 0) {
            mw.innerHTML = '<p>Нет пользователей.</p>';
            return;
        }
        let rows = data.members.map(function (m) {
            const st = escapeHtml(m.registrationStatus) + (m.active ? '' : ' · неактивен');
            const who = escapeHtml(m.lastName) + ' ' + escapeHtml(m.firstName) + ' ' + escapeHtml(m.middleName);
            const dis = m.currentUser;
            return '<tr data-id="' + m.id + '">' +
                '<td>' + who.trim() + '</td>' +
                '<td>' + escapeHtml(m.login) + '</td>' +
                '<td>' + escapeHtml(m.email) + '</td>' +
                '<td>' + escapeHtml(st) + '</td>' +
                '<td>' + roleSelectHtml(m.role, m.id, dis) + '</td>' +
                '<td><button type="button" class="primary" data-act="save-role" data-id="' + m.id + '"' + (dis ? ' disabled' : '') + '>Сохранить роль</button></td>' +
                '<td><button type="button" class="danger" data-act="delete" data-id="' + m.id + '"' + (dis ? ' disabled' : '') + '>Удалить</button></td>' +
                '</tr>';
        }).join('');
        mw.innerHTML = '<table><thead><tr><th>ФИО</th><th>Логин</th><th>Email</th><th>Статус</th><th>Роль</th><th></th><th></th></tr></thead><tbody>' + rows + '</tbody></table>';

        document.querySelectorAll('button[data-act="save-role"]').forEach(function (btn) {
            btn.addEventListener('click', onSaveRole);
        });
        document.querySelectorAll('button[data-act="delete"]').forEach(function (btn) {
            btn.addEventListener('click', onDelete);
        });
    }

    function selectForRow(userId) {
        return document.querySelector('select[data-user-id="' + userId + '"]');
    }

    /** POST с разбором ответа: у бэкенда ошибки API могут идти как 200 + { ok: false }. */
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
            showBanner('Ошибка ' + res.status + (payload && payload.detail ? ': ' + payload.detail : ''), true);
            return;
        }
        if (payload && payload.message) {
            showBanner(payload.message, payload.ok === false);
        }
        await loadSummary();
    }

    function onSaveRole(ev) {
        const btn = ev.currentTarget;
        const id = parseInt(btn.getAttribute('data-id'), 10);
        const sel = selectForRow(id);
        if (!sel) return;
        apiPost('/role', { userId: id, role: sel.value });
    }

    function onDelete(ev) {
        const btn = ev.currentTarget;
        const id = parseInt(btn.getAttribute('data-id'), 10);
        if (!confirm('Удалить пользователя #' + id + '? Действие необратимо.')) return;
        apiPost('/delete', { userId: id });
    }

    loadSummary().catch(function () {
        showBanner('Ошибка сети при загрузке страницы.', true);
    });
})();
