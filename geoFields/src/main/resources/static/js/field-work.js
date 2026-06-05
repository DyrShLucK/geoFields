/**
 * Операции по полю — страница /org/field-work (API /api/field-work).
 */
(function () {
    const API = '/api/field-work';
    const SUMMARY_API = '/api/org/agronomist';
    const fetchOpts = { credentials: 'same-origin' };

    let lastCsrf = null;
    let summaryData = null;
    let catalog = null;
    let selectedFieldId = null;
    let lastOperations = [];
    let editingOperationId = null;
    let loadSeq = 0;

    const OPS_TABLE_HEAD = `
        <table class="history-table operations-table">
            <thead>
                <tr>
                    <th>Дата</th>
                    <th>Название</th>
                    <th>Категория</th>
                    <th>Статус</th>
                    <th>Действия</th>
                </tr>
            </thead>
            <tbody></tbody>
        </table>`;

    function escapeHtml(s) {
        if (s == null) return '';
        return String(s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function showBanner(text, isError) {
        const el = document.getElementById('banner');
        if (!el) return;
        if (!text) {
            el.innerHTML = '';
            return;
        }
        el.innerHTML = '<div class="msg ' + (isError ? 'err' : 'ok') + '">' + escapeHtml(text) + '</div>';
    }

    function mutatingHeaders() {
        const headers = csrfHeaders({ 'Content-Type': 'application/json' });
        const headerName = lastCsrf && lastCsrf.headerName ? lastCsrf.headerName : 'X-XSRF-TOKEN';
        const token = (lastCsrf && lastCsrf.token) ? lastCsrf.token : readXsrfToken();
        if (token) headers[headerName] = token;
        return headers;
    }

    function formatDateTime(iso) {
        if (!iso) return '—';
        const d = new Date(iso);
        if (Number.isNaN(d.getTime())) return String(iso);
        return d.toLocaleString('ru-RU', {
            year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit'
        });
    }

    function toDatetimeLocalValue(iso) {
        if (!iso) return '';
        const d = new Date(iso);
        if (Number.isNaN(d.getTime())) return '';
        const pad = n => String(n).padStart(2, '0');
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
    }

    function readDatetimeLocalAsIso(value) {
        if (!value) return null;
        const d = new Date(value);
        if (Number.isNaN(d.getTime())) return null;
        const pad = n => String(n).padStart(2, '0');
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`;
    }

    function ensureOpsTable() {
        const wrap = document.getElementById('operations-wrap');
        if (!wrap.querySelector('.operations-table')) {
            wrap.innerHTML = OPS_TABLE_HEAD;
        }
        return wrap.querySelector('tbody');
    }

    function setOpsBody(html) {
        ensureOpsTable().innerHTML = html;
    }

    function setLoadButtonBusy(busy) {
        const btn = document.getElementById('btn-load-operations');
        if (!btn) return;
        btn.disabled = busy;
        btn.textContent = busy ? 'Загрузка…' : 'Показать';
    }

    function hideOperationsPanel() {
        loadSeq++;
        document.getElementById('operations-panel').classList.add('is-collapsed');
        document.getElementById('operations-filters')?.classList.add('is-collapsed');
        document.getElementById('status-history-panel').classList.add('is-collapsed');
        setLoadButtonBusy(false);
        selectedFieldId = null;
        editingOperationId = null;
    }

    function showOperationsPanel(fieldId) {
        selectedFieldId = fieldId;
        document.getElementById('operations-panel').classList.remove('is-collapsed');
        document.getElementById('operations-filters')?.classList.remove('is-collapsed');
        const name = getFieldName(fieldId);
        document.getElementById('selected-field-name').textContent = name;
        setOpsBody('<tr class="history-status-row"><td colspan="5">Загрузка…</td></tr>');
    }

    function readOperationFilters() {
        return {
            category: document.getElementById('op-filter-category')?.value || '',
            status: document.getElementById('op-filter-status')?.value || '',
            dateFrom: document.getElementById('op-filter-date-from')?.value || '',
            dateTo: document.getElementById('op-filter-date-to')?.value || '',
            query: (document.getElementById('op-filter-name')?.value || '').trim().toLowerCase()
        };
    }

    function filterOperations(rows, filters) {
        return rows.filter(op => {
            if (filters.category && op.category !== filters.category) return false;
            if (filters.status && op.status !== filters.status) return false;
            if (filters.query) {
                const name = String(op.name || '').toLowerCase();
                if (!name.includes(filters.query)) return false;
            }
            if (filters.dateFrom) {
                const d = new Date(op.operationAt);
                const from = new Date(filters.dateFrom + 'T00:00:00');
                if (!Number.isNaN(d.getTime()) && !Number.isNaN(from.getTime()) && d < from) return false;
            }
            if (filters.dateTo) {
                const d = new Date(op.operationAt);
                const to = new Date(filters.dateTo + 'T23:59:59');
                if (!Number.isNaN(d.getTime()) && !Number.isNaN(to.getTime()) && d > to) return false;
            }
            return true;
        });
    }

    function resetOperationFilters() {
        const ids = ['op-filter-category', 'op-filter-status', 'op-filter-date-from', 'op-filter-date-to', 'op-filter-name'];
        ids.forEach(id => {
            const el = document.getElementById(id);
            if (!el) return;
            if (el.tagName === 'SELECT') el.value = '';
            else el.value = '';
        });
        applyOperationFiltersAndRender();
    }

    function updateOperationFilterHint(shown, total) {
        const hint = document.getElementById('op-filter-hint');
        if (!hint) return;
        if (total === 0) {
            hint.textContent = '';
            return;
        }
        if (shown === total) {
            hint.textContent = `Показано операций: ${total}`;
        } else {
            hint.textContent = `Показано ${shown} из ${total}`;
        }
    }

    function applyOperationFiltersAndRender() {
        const filters = readOperationFilters();
        const filtered = filterOperations(lastOperations, filters);
        updateOperationFilterHint(filtered.length, lastOperations.length);
        renderOperations(filtered);
    }

    function getFieldName(fieldId) {
        const field = (summaryData?.fields || []).find(f => Number(f.fieldId) === Number(fieldId));
        return field ? field.fieldName : ('Поле #' + fieldId);
    }

    function fillCatalogSelects() {
        if (!catalog) return;
        const cat = document.getElementById('form-category');
        const st = document.getElementById('form-status');
        cat.innerHTML = (catalog.categories || []).map(c =>
            `<option value="${escapeHtml(c.code)}">${escapeHtml(c.titleRu)}</option>`).join('');
        st.innerHTML = (catalog.statuses || []).map(s =>
            `<option value="${escapeHtml(s.code)}">${escapeHtml(s.titleRu)}</option>`).join('');
        if (!st.querySelector('option[value="PLANNED"]')) {
            st.insertAdjacentHTML('afterbegin', '<option value="PLANNED">Запланировано</option>');
        }
        const filterCat = document.getElementById('op-filter-category');
        const filterSt = document.getElementById('op-filter-status');
        if (filterCat) {
            filterCat.innerHTML = '<option value="">Все</option>' +
                (catalog.categories || []).map(c =>
                    `<option value="${escapeHtml(c.code)}">${escapeHtml(c.titleRu)}</option>`).join('');
        }
        if (filterSt) {
            filterSt.innerHTML = '<option value="">Все</option>' +
                (catalog.statuses || []).map(s =>
                    `<option value="${escapeHtml(s.code)}">${escapeHtml(s.titleRu)}</option>`).join('');
        }
    }

    function fillFieldSelect() {
        const fs = document.getElementById('field-select');
        const prev = fs.value;
        fs.innerHTML = '<option value="">— выберите поле —</option>' +
            (summaryData?.fields || []).map(f =>
                `<option value="${f.fieldId}">${escapeHtml(f.fieldName)}</option>`).join('');
        if (prev && fs.querySelector(`option[value="${prev}"]`)) {
            fs.value = prev;
        }
    }

    async function loadSummary() {
        const res = await fetch(SUMMARY_API + '/summary', fetchOpts);
        if (!res.ok) {
            showBanner('Не удалось загрузить данные организации (HTTP ' + res.status + ').', true);
            return;
        }
        summaryData = await res.json();
        lastCsrf = summaryData.csrf || null;
        document.getElementById('org-name').textContent =
            summaryData.organizationName || ('#' + summaryData.organizationId);
        const ul = document.getElementById('user-label');
        if (ul) ul.textContent = summaryData.currentUserFullName || summaryData.currentLogin || '';
        fillFieldSelect();
    }

    async function loadCatalog() {
        const res = await fetch(API + '/catalog', fetchOpts);
        if (!res.ok) {
            showBanner('Не удалось загрузить справочники операций.', true);
            return;
        }
        catalog = await res.json();
        fillCatalogSelects();
    }

    function renderOperations(rows) {
        if (!rows.length) {
            const msg = lastOperations.length
                ? 'Нет операций по выбранным фильтрам.'
                : 'Операций пока нет. Заполните форму ниже.';
            setOpsBody('<tr class="history-status-row"><td colspan="5" class="history-empty">' + escapeHtml(msg) + '</td></tr>');
            return;
        }
        const trs = rows.map(op => `
            <tr>
                <td>${formatDateTime(op.operationAt)}</td>
                <td>${escapeHtml(op.name)}</td>
                <td>${escapeHtml(op.categoryTitleRu || op.category)}</td>
                <td><span class="op-status-badge op-status-badge--${String(op.status || '').replace(/[^A-Za-z0-9_-]/g, '')}">${escapeHtml(op.statusTitleRu || op.status)}</span></td>
                <td class="history-actions">
                    <div class="history-actions-inner">
                        <button type="button" class="btn" data-act="journal" data-id="${op.id}">Журнал</button>
                        <button type="button" class="btn primary" data-act="edit" data-id="${op.id}">Изменить</button>
                        <button type="button" class="btn danger" data-act="del" data-id="${op.id}">Удалить</button>
                    </div>
                </td>
            </tr>
        `).join('');
        setOpsBody(trs);
        bindRowActions();
    }

    function bindRowActions() {
        const wrap = document.getElementById('operations-wrap');
        wrap.querySelectorAll('button[data-act="edit"]').forEach(btn => {
            btn.addEventListener('click', () => {
                const id = parseInt(btn.getAttribute('data-id'), 10);
                const row = lastOperations.find(x => Number(x.id) === id);
                if (row) enterEditMode(row);
            });
        });
        wrap.querySelectorAll('button[data-act="del"]').forEach(btn => {
            btn.addEventListener('click', () => deleteOperation(parseInt(btn.getAttribute('data-id'), 10)));
        });
        wrap.querySelectorAll('button[data-act="journal"]').forEach(btn => {
            btn.addEventListener('click', () => loadStatusJournal(parseInt(btn.getAttribute('data-id'), 10)));
        });
    }

    async function loadOperations(options) {
        const silent = options && options.silent === true;
        const fieldId = parseInt(document.getElementById('field-select').value, 10);
        if (!fieldId) {
            if (!silent) showBanner('Выберите поле из списка.', true);
            return;
        }

        const seq = ++loadSeq;
        if (!silent) {
            showOperationsPanel(fieldId);
            setLoadButtonBusy(true);
        }

        try {
            const res = await fetch(`${API}/fields/${fieldId}/items`, fetchOpts);
            if (seq !== loadSeq) return;
            if (!res.ok) {
                setOpsBody('<tr class="history-status-row"><td colspan="5">Ошибка загрузки (HTTP ' + res.status + ').</td></tr>');
                if (!silent) showBanner('Не удалось загрузить операции.', true);
                return;
            }
            lastOperations = await res.json();
            if (seq !== loadSeq) return;
            applyOperationFiltersAndRender();
            if (!silent) resetFormMode();
            showBanner('', false);
        } catch (_e) {
            if (seq !== loadSeq) return;
            setOpsBody('<tr class="history-status-row"><td colspan="5">Ошибка сети.</td></tr>');
            if (!silent) showBanner('Ошибка сети при загрузке операций.', true);
        } finally {
            if (!silent && seq === loadSeq) setLoadButtonBusy(false);
        }
    }

    function resetFormMode() {
        editingOperationId = null;
        document.getElementById('form-title').textContent = 'Новая операция';
        document.getElementById('form-name').value = '';
        document.getElementById('form-status-note').value = '';
        const st = document.getElementById('form-status');
        if (st) st.value = 'PLANNED';
        const now = new Date();
        document.getElementById('form-operation-at').value = toDatetimeLocalValue(now.toISOString());
        document.getElementById('status-history-panel').classList.add('is-collapsed');
    }

    function enterEditMode(op) {
        editingOperationId = op.id;
        document.getElementById('form-title').textContent = 'Редактирование операции #' + op.id;
        document.getElementById('form-name').value = op.name || '';
        document.getElementById('form-category').value = op.category || '';
        document.getElementById('form-status').value = op.status || 'PLANNED';
        document.getElementById('form-operation-at').value = toDatetimeLocalValue(op.operationAt);
        document.getElementById('form-status-note').value = '';
        loadStatusJournal(op.id, op.name);
        document.getElementById('operations-panel').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    function readFormPayload() {
        return {
            name: document.getElementById('form-name').value.trim(),
            category: document.getElementById('form-category').value,
            status: document.getElementById('form-status').value,
            operationAt: readDatetimeLocalAsIso(document.getElementById('form-operation-at').value),
            statusNote: document.getElementById('form-status-note').value.trim() || null
        };
    }

    async function saveOperation() {
        if (!selectedFieldId) {
            showBanner('Сначала выберите поле и загрузите список.', true);
            return;
        }
        const payload = readFormPayload();
        if (!payload.name) {
            showBanner('Укажите название операции.', true);
            return;
        }
        if (!payload.operationAt) {
            showBanner('Укажите дату и время.', true);
            return;
        }

        const url = editingOperationId
            ? `${API}/items/${editingOperationId}`
            : `${API}/fields/${selectedFieldId}/items`;
        const method = editingOperationId ? 'PUT' : 'POST';

        try {
            const res = await fetch(url, {
                method,
                headers: mutatingHeaders(),
                credentials: 'same-origin',
                body: JSON.stringify(payload)
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                showBanner(data.message || data.error || ('Ошибка сохранения (HTTP ' + res.status + ')'), true);
                return;
            }
            showBanner(data.message || 'Сохранено', false);
            await loadOperations({ silent: true });
            if (editingOperationId) {
                const op = lastOperations.find(x => Number(x.id) === Number(editingOperationId));
                if (op) loadStatusJournal(editingOperationId, op.name);
            } else {
                resetFormMode();
            }
        } catch (_e) {
            showBanner('Ошибка сети при сохранении.', true);
        }
    }

    async function deleteOperation(operationId) {
        if (!confirm('Удалить эту операцию? Журнал статусов также будет удалён.')) return;
        try {
            const res = await fetch(`${API}/items/${operationId}`, {
                method: 'DELETE',
                headers: mutatingHeaders(),
                credentials: 'same-origin'
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                showBanner(data.message || 'Не удалось удалить', true);
                return;
            }
            showBanner(data.message || 'Удалено', false);
            if (editingOperationId === operationId) {
                resetFormMode();
                document.getElementById('status-history-panel').classList.add('is-collapsed');
            }
            await loadOperations({ silent: true });
        } catch (_e) {
            showBanner('Ошибка сети при удалении.', true);
        }
    }

    async function loadStatusJournal(operationId, operationName) {
        const panel = document.getElementById('status-history-panel');
        const list = document.getElementById('status-history-list');
        const caption = document.getElementById('status-history-caption');
        panel.classList.remove('is-collapsed');
        const label = operationName || ('#' + operationId);
        caption.textContent = 'Операция: ' + label;
        list.innerHTML = '<li class="status-history-empty">Загрузка…</li>';

        try {
            const res = await fetch(`${API}/items/${operationId}`, fetchOpts);
            if (!res.ok) {
                list.innerHTML = '<li class="status-history-empty">Не удалось загрузить журнал.</li>';
                return;
            }
            const detail = await res.json();
            const history = detail.statusHistory || [];
            if (!history.length) {
                list.innerHTML = '<li class="status-history-empty">Записей в журнале пока нет.</li>';
                return;
            }
            list.innerHTML = history.map((h, idx) => `
                <li class="status-history-item">
                    <div class="status-history-dot" aria-hidden="true"></div>
                    <div class="status-history-body">
                        <strong>${escapeHtml(h.statusTitleRu || h.status)}</strong>
                        <span class="status-history-time">${formatDateTime(h.changedAt)}</span>
                        ${h.userDisplayName ? `<span class="status-history-user">${escapeHtml(h.userDisplayName)}</span>` : ''}
                        ${h.note ? `<p class="status-history-note">${escapeHtml(h.note)}</p>` : ''}
                    </div>
                </li>
            `).join('');
        } catch (_e) {
            list.innerHTML = '<li class="status-history-empty">Ошибка сети.</li>';
        }
    }

    function init() {
        document.getElementById('btn-load-operations').addEventListener('click', () => loadOperations());
        document.getElementById('field-select').addEventListener('change', () => {
            hideOperationsPanel();
            showBanner('', false);
        });
        ['op-filter-category', 'op-filter-status', 'op-filter-date-from', 'op-filter-date-to'].forEach(id => {
            document.getElementById(id)?.addEventListener('change', applyOperationFiltersAndRender);
        });
        document.getElementById('op-filter-name')?.addEventListener('input', applyOperationFiltersAndRender);
        document.getElementById('btn-reset-op-filters')?.addEventListener('click', resetOperationFilters);
        document.getElementById('btn-save-operation').addEventListener('click', saveOperation);
        document.getElementById('btn-cancel-edit').addEventListener('click', resetFormMode);
        document.getElementById('btn-new-operation').addEventListener('click', resetFormMode);

        Promise.all([loadSummary(), loadCatalog()]).then(() => {
            const params = new URLSearchParams(window.location.search);
            const fieldId = params.get('fieldId');
            if (fieldId && document.getElementById('field-select').querySelector(`option[value="${fieldId}"]`)) {
                document.getElementById('field-select').value = fieldId;
                loadOperations();
            }
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
