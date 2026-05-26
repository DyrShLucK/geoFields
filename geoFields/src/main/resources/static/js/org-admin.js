/**
 * Администрирование организации: Управление пользователями и земельным фондом.
 */
(function () {
    const API = '/api/org/admin';
    const AGRONOMIST_API = '/api/org/agronomist';
    const fetchOpts = { credentials: 'same-origin' };

    const ROLE_OPTIONS = [
        { value: 'USER', label: 'Пользователь' },
        { value: 'AGRONOMIST', label: 'Агроном' },
        { value: 'ORG_MANAGER', label: 'Менеджер' },
        { value: 'ORG_ADMIN', label: 'Администратор' },
    ];

    function postHeaders(csrfFromSummary) {
        const headers = csrfHeaders({ 'Content-Type': 'application/json' });
        const headerName = csrfFromSummary && csrfFromSummary.headerName ? csrfFromSummary.headerName : 'X-XSRF-TOKEN';
        const token = (csrfFromSummary && csrfFromSummary.token) ? csrfFromSummary.token : readXsrfToken();
        if (token) {
            headers[headerName] = token;
        }
        return headers;
    }

    let lastCsrf = null;
    let lastFields = [];

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

    function roleSelectHtml(currentRole, userId, disabled) {
        let opts = ROLE_OPTIONS.map(function (o) {
            const sel = o.value === currentRole ? ' selected' : '';
            return '<option value="' + escapeHtml(o.value) + '"' + sel + '>' + escapeHtml(o.label) + '</option>';
        }).join('');
        return '<select class="form-input" data-user-id="' + userId + '" data-kind="role"' + (disabled ? ' disabled' : '') + '>' + opts + '</select>';
    }

    async function loadSummary() {
        showBanner('', false);
        const res = await fetch(API + '/summary', fetchOpts);
        if (!res.ok) {
            document.getElementById('members-wrap').innerHTML = '<p class="muted">Не удалось загрузить данные участников.</p>';
            return;
        }
        const data = await res.json();
        lastCsrf = data.csrf || null;
        document.getElementById('org-name').textContent = data.organizationName || ('#' + data.organizationId);
        
        const ul = document.getElementById('user-label');
        if (ul) {
            ul.textContent = 'Вы: ' + (data.currentUserFullName || data.currentLogin || '');
        }

        const mw = document.getElementById('members-wrap');
        if (!data.members || data.members.length === 0) {
            mw.innerHTML = '<p class="muted">Пользователи отсутствуют.</p>';
        } else {
            let rows = data.members.map(function (m) {
                const st = escapeHtml(m.registrationStatus) + (m.active ? '' : ' · неактивен');
                const who = escapeHtml(m.lastName) + ' ' + escapeHtml(m.firstName) + ' ' + (m.middleName ? escapeHtml(m.middleName) : '');
                const dis = m.currentUser;
                return '<tr data-id="' + m.id + '">' +
                    '<td>' + who.trim() + '</td>' +
                    '<td>' + escapeHtml(m.login) + '</td>' +
                    '<td>' + escapeHtml(m.email) + '</td>' +
                    '<td>' + escapeHtml(st) + '</td>' +
                    '<td>' + roleSelectHtml(m.role, m.id, dis) + '</td>' +
                    '<td><button type="button" class="btn primary" data-act="save-role" data-id="' + m.id + '"' + (dis ? ' disabled' : '') + '>Сохранить</button></td>' +
                    '<td><button type="button" class="btn danger" data-act="delete" data-id="' + m.id + '"' + (dis ? ' disabled' : '') + '>Удалить</button></td>' +
                    '</tr>';
            }).join('');
            mw.innerHTML = '<table><thead><tr><th>ФИО</th><th>Логин</th><th>Email</th><th>Статус</th><th>Права доступа</th><th></th><th></th></tr></thead><tbody>' + rows + '</tbody></table>';

            document.querySelectorAll('button[data-act="save-role"]').forEach(function (btn) {
                btn.addEventListener('click', onSaveRole);
            });
            document.querySelectorAll('button[data-act="delete"]').forEach(function (btn) {
                btn.addEventListener('click', onDelete);
            });
        }
        await loadFields();
    }

    async function loadFields() {
        const res = await fetch(AGRONOMIST_API + '/summary', fetchOpts);
        if (!res.ok) return;
        const data = await res.json();
        lastFields = data.fields || [];
        
        const sel = document.getElementById('field-select');
        sel.innerHTML = '<option value="">— выберите поле из списка —</option>' +
            lastFields.map(function (f) {
                return '<option value="' + f.fieldId + '">' + escapeHtml(f.fieldName) + '</option>';
            }).join('');
    }

    function onSaveRole(ev) {
        const btn = ev.currentTarget;
        const id = parseInt(btn.getAttribute('data-id'), 10);
        const sel = document.querySelector('select[data-user-id="' + id + '"]');
        if (!sel) return;
        apiJsonCall('POST', '/role', { userId: id, role: sel.value });
    }

    function onDelete(ev) {
        const btn = ev.currentTarget;
        const id = parseInt(btn.getAttribute('data-id'), 10);
        if (!confirm('Внимание: Вы действительно хотите удалить аккаунт #' + id + '? Это действие необратимо.')) return;
        apiJsonCall('POST', '/delete', { userId: id });
    }

    async function apiJsonCall(method, path, body) {
        const res = await fetch(API + path, {
            method: method,
            credentials: 'same-origin',
            headers: postHeaders(lastCsrf),
            body: body == null ? undefined : JSON.stringify(body),
        });
        const text = await res.text();
        let payload = null;
        try { payload = text ? JSON.parse(text) : null; } catch (_) {}

        if (!res.ok) {
            showBanner('Ошибка операции: ' + (payload ? payload.message : res.status), true);
            return;
        }
        showBanner('Успешно выполнено.', false);
        await loadSummary();
    }

    document.getElementById('btn-delete-field').addEventListener('click', function () {
        const sel = document.getElementById('field-select');
        const fieldId = parseInt(sel.value, 10);
        if (!fieldId) {
            showBanner('Ошибка: Не выбрано поле.', true);
            return;
        }
        if (!confirm('Внимание: Удалить выбранное поле? Все связанные севообороты и логи также сотрутся.')) return;
        apiJsonCall('DELETE', '/fields/' + fieldId, null);
    });

    loadSummary().catch(function () {
        showBanner('Сбой сети при получении информации.', true);
    });
})();