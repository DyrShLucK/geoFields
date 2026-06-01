/**
 * Панель агронома: Управление журналом севооборота и архивация земель.
 */
(function () {
    const API = '/api/org/agronomist';
    const fetchOpts = { credentials: 'same-origin' };

    let lastCsrf = null;
    let summaryData = null;
    let selectedFieldId = null;
    let lastHistoryRows = [];
    let editingHistoryId = null;
    let historyLoadSeq = 0;

    const HISTORY_TABLE_HEAD = `
        <table class="history-table">
            <thead>
                <tr>
                    <th>Год</th>
                    <th>Культура</th>
                    <th>Сев</th>
                    <th>Жатва</th>
                    <th>Площ., га</th>
                    <th>Урож.</th>
                    <th>Действия</th>
                </tr>
            </thead>
            <tbody></tbody>
        </table>`;

    function setLoadButtonBusy(isBusy) {
        const btn = document.getElementById('btn-load-history');
        if (!btn) return;
        btn.disabled = isBusy;
        btn.textContent = isBusy ? 'Загрузка…' : 'Показать';
    }

    function ensureHistoryTable() {
        const hw = document.getElementById('history-wrap');
        let table = hw.querySelector('.history-table');
        if (!table) {
            hw.innerHTML = HISTORY_TABLE_HEAD;
            table = hw.querySelector('.history-table');
        }
        return table.querySelector('tbody');
    }

    function setHistoryBody(html) {
        ensureHistoryTable().innerHTML = html;
    }

    function bindHistoryRowActions() {
        const hw = document.getElementById('history-wrap');
        hw.querySelectorAll('button[data-act="edit"]').forEach(btn => {
            btn.addEventListener('click', () => {
                const id = parseInt(btn.getAttribute('data-id'), 10);
                const row = lastHistoryRows.find(x => x.historyId === id);
                if (row) enterEditMode(row);
            });
        });
        hw.querySelectorAll('button[data-act="del"]').forEach(btn => {
            btn.addEventListener('click', () => deleteRecord(parseInt(btn.getAttribute('data-id'), 10)));
        });
    }

    function renderHistoryRows(rows) {
        if (!rows.length) {
            setHistoryBody('<tr class="history-status-row"><td colspan="7" class="history-empty">Записей посева пока нет. Заполните форму ниже и нажмите «Сохранить», чтобы добавить первую запись.</td></tr>');
            return;
        }

        const trs = rows.map(r => `
            <tr>
                <td>${r.cropYear || '—'}</td>
                <td>${escapeHtml(r.cropName || '—')}</td>
                <td>${r.sowingDate || '—'}</td>
                <td>${r.harvestDate || '—'}</td>
                <td>${r.sownAreaHa != null ? r.sownAreaHa : '—'}</td>
                <td>${r.actualYield != null ? r.actualYield : '—'}</td>
                <td class="history-actions">
                    <div class="history-actions-inner">
                        <button type="button" class="btn primary" data-act="edit" data-id="${r.historyId}">Изменить</button>
                        <button type="button" class="btn danger" data-act="del" data-id="${r.historyId}">Удалить</button>
                    </div>
                </td>
            </tr>
        `).join('');
        setHistoryBody(trs);
        bindHistoryRowActions();
    }

    function showFieldSection(fieldId) {
        selectedFieldId = fieldId;
        document.getElementById('field-actions').classList.remove('is-collapsed');
        document.getElementById('field-work-panel').classList.remove('is-collapsed');

        const fieldName = getSelectedFieldName(fieldId);
        const titleEl = document.getElementById('history-section-title');
        if (titleEl) titleEl.textContent = 'Севооборот: ' + fieldName;

        const field = (summaryData?.fields || []).find(f => Number(f.fieldId) === Number(fieldId));
        const nameEl = document.getElementById('selected-field-name');
        if (nameEl) nameEl.textContent = field ? field.fieldName : ('#' + fieldId);

        setHistoryBody('<tr class="history-status-row"><td colspan="7">Загрузка…</td></tr>');
    }

    function mutatingHeaders() {
        const headers = csrfHeaders({ 'Content-Type': 'application/json' });
        const headerName = lastCsrf && lastCsrf.headerName ? lastCsrf.headerName : 'X-XSRF-TOKEN';
        const token = (lastCsrf && lastCsrf.token) ? lastCsrf.token : readXsrfToken();
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
        const res = await fetch(API + '/summary', fetchOpts);
        if (!res.ok) return;
        summaryData = await res.json();
        lastCsrf = summaryData.csrf || null;

        document.getElementById('org-name').textContent = summaryData.organizationName || ('#' + summaryData.organizationId);
        
        const ul = document.getElementById('user-label');
        if (ul) ul.textContent = summaryData.currentUserFullName || summaryData.currentLogin || '';

        // Заполнение селектов полей
        fillFieldSelects();
    }

    function fillFieldSelects() {
        const fs = document.getElementById('field-select');
        const ofs = document.getElementById('obsolete-field-select');
        const cs = document.getElementById('form-crop');
        const prevField = fs.value;
        const prevObsolete = ofs.value;

        fs.innerHTML = '<option value="">— Выбрать активное поле —</option>' +
            (summaryData.fields || []).map(f => `<option value="${f.fieldId}">${escapeHtml(f.fieldName)}</option>`).join('');

        ofs.innerHTML = '<option value="">— Архивные поля отсутствуют —</option>' +
            (summaryData.obsoleteFields || []).map(f => `<option value="${f.fieldId}">${escapeHtml(f.fieldName)}</option>`).join('');

        cs.innerHTML = (summaryData.crops || []).map(c => `<option value="${c.cropId}">${escapeHtml(c.cropName)}</option>`).join('');

        if (prevField && fs.querySelector(`option[value="${prevField}"]`)) {
            fs.value = prevField;
        }
        if (prevObsolete && ofs.querySelector(`option[value="${prevObsolete}"]`)) {
            ofs.value = prevObsolete;
        }
    }

    function getSelectedFieldName(fieldId) {
        const field = (summaryData?.fields || []).find(f => Number(f.fieldId) === Number(fieldId));
        return field ? field.fieldName : ('Поле #' + fieldId);
    }

    async function loadHistory(options) {
        const silent = options && options.silent === true;
        const fieldId = parseInt(document.getElementById('field-select').value, 10);
        if (!fieldId) {
            if (!silent) showBanner('Выберите поле из списка.', true);
            return;
        }

        const loadId = ++historyLoadSeq;
        if (!silent) {
            showFieldSection(fieldId);
            setLoadButtonBusy(true);
        }

        try {
            const res = await fetch(`${API}/fields/${fieldId}/history`, fetchOpts);
            if (loadId !== historyLoadSeq) return;

            if (!res.ok) {
                setHistoryBody('<tr class="history-status-row"><td colspan="7">Не удалось загрузить историю (HTTP ' + res.status + ').</td></tr>');
                if (!silent) showBanner('Не удалось загрузить историю посевов (HTTP ' + res.status + ').', true);
                return;
            }

            const rows = await res.json();
            if (loadId !== historyLoadSeq) return;

            lastHistoryRows = rows;
            renderHistoryRows(rows);
            if (!silent) resetFormMode();
            showBanner('', false);
        } catch (_err) {
            if (loadId !== historyLoadSeq) return;
            setHistoryBody('<tr class="history-status-row"><td colspan="7">Ошибка сети при загрузке истории.</td></tr>');
            if (!silent) showBanner('Ошибка сети при загрузке истории.', true);
        } finally {
            if (!silent && loadId === historyLoadSeq) {
                setLoadButtonBusy(false);
            }
        }
    }

    function hideFieldWorkPanel() {
        historyLoadSeq++;
        document.getElementById('field-work-panel').classList.add('is-collapsed');
        document.getElementById('field-actions').classList.add('is-collapsed');
        setLoadButtonBusy(false);
        selectedFieldId = null;
    }

    function readFormPayload() {
        const cropYear = parseInt(document.getElementById('form-year').value, 10);
        return {
            cropId: parseInt(document.getElementById('form-crop').value, 10),
            sowingDate: document.getElementById('form-sowing').value || null,
            harvestDate: document.getElementById('form-harvest').value || null,
            sownAreaHa: parseFloat(document.getElementById('form-sown-ha').value) || null,
            harvestAreaHa: parseFloat(document.getElementById('form-harvest-ha').value) || null,
            actualYield: parseFloat(document.getElementById('form-actual-yield').value) || null,
            totalYield: parseFloat(document.getElementById('form-total-yield').value) || null,
            plannedYield: parseFloat(document.getElementById('form-planned-yield').value) || null,
            forecastedYield: parseFloat(document.getElementById('form-forecast-yield').value) || null,
            sourceData: document.getElementById('form-source').value || null,
            sowingDetails: document.getElementById('form-details').value || null,
            cropYear: Number.isFinite(cropYear) ? cropYear : null
        };
    }

    function resetFormMode() {
        editingHistoryId = null;
        const title = document.getElementById('form-title');
        if (title) title.textContent = 'Новая запись посева';
        document.getElementById('form-year').value = '';
        document.getElementById('form-sowing').value = '';
        document.getElementById('form-harvest').value = '';
        document.getElementById('form-sown-ha').value = '';
        document.getElementById('form-harvest-ha').value = '';
        document.getElementById('form-actual-yield').value = '';
        document.getElementById('form-total-yield').value = '';
        document.getElementById('form-planned-yield').value = '';
        document.getElementById('form-forecast-yield').value = '';
        document.getElementById('form-source').value = '';
        document.getElementById('form-details').value = '';
    }

    async function saveRecord() {
        if (!selectedFieldId) {
            showBanner('Сначала выберите поле и загрузите историю.', true);
            return;
        }
        const payload = readFormPayload();
        if (!payload.cropId || payload.cropYear == null) {
            showBanner('Укажите культуру и год поля (crop_year).', true);
            return;
        }
        const url = editingHistoryId
            ? `${API}/history/${editingHistoryId}`
            : `${API}/fields/${selectedFieldId}/history`;
        const res = await fetch(url, {
            method: editingHistoryId ? 'PUT' : 'POST',
            credentials: 'same-origin',
            headers: mutatingHeaders(),
            body: JSON.stringify(payload)
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showBanner(data.message || data.detail || 'Не удалось сохранить запись.', true);
            return;
        }
        showBanner(data.message || 'Запись сохранена.', false);
        resetFormMode();
        await loadHistory({ silent: true });
    }

    async function markFieldObsolete() {
        const fieldId = parseInt(document.getElementById('field-select').value, 10);
        if (!fieldId) return;
        if (!confirm('Пометить поле как устаревшее? Оно исчезнет с карты и из списков активных.')) return;

        const res = await fetch(`${API}/fields/${fieldId}/status`, {
            method: 'PUT',
            credentials: 'same-origin',
            headers: mutatingHeaders(),
            body: JSON.stringify({ status: 'OBSOLETE' })
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showBanner(data.message || data.detail || 'Не удалось обновить статус поля.', true);
            return;
        }

        showBanner(data.message || 'Поле помечено как устаревшее.', false);
        selectedFieldId = null;
        document.getElementById('field-select').value = '';
        hideFieldWorkPanel();
        resetFormMode();
        await loadSummary();
    }

    async function restoreObsoleteField() {
        const fieldId = parseInt(document.getElementById('obsolete-field-select').value, 10);
        if (!fieldId) {
            showBanner('Выберите устаревшее поле.', true);
            return;
        }
        if (!confirm('Вернуть поле в активные?')) return;

        const res = await fetch(`${API}/fields/${fieldId}/status`, {
            method: 'PUT',
            credentials: 'same-origin',
            headers: mutatingHeaders(),
            body: JSON.stringify({ status: 'ACTIVE' })
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showBanner(data.message || data.detail || 'Не удалось восстановить поле.', true);
            return;
        }

        showBanner(data.message || 'Поле снова активно.', false);
        document.getElementById('obsolete-field-select').value = '';
        await loadSummary();
    }

    function enterEditMode(row) {
        editingHistoryId = row.historyId;
        document.getElementById('form-title').textContent = 'Редактировать запись сева #' + row.historyId;
        document.getElementById('form-crop').value = row.cropId;
        document.getElementById('form-year').value = row.cropYear || '';
        document.getElementById('form-sowing').value = row.sowingDate || '';
        document.getElementById('form-harvest').value = row.harvestDate || '';
        document.getElementById('form-sown-ha').value = row.sownAreaHa || '';
        document.getElementById('form-harvest-ha').value = row.harvestAreaHa || '';
        document.getElementById('form-actual-yield').value = row.actualYield || '';
        document.getElementById('form-total-yield').value = row.totalYield || '';
        document.getElementById('form-planned-yield').value = row.plannedYield || '';
        document.getElementById('form-forecast-yield').value = row.forecastedYield || '';
        document.getElementById('form-source').value = row.sourceData || '';
        document.getElementById('form-details').value = row.sowingDetails || '';
    }

    async function deleteRecord(id) {
        if (!confirm('Вы уверены, что хотите удалить запись сева?')) return;
        const res = await fetch(`${API}/history/${id}`, {
            method: 'DELETE',
            credentials: 'same-origin',
            headers: mutatingHeaders()
        });
        if (res.ok) {
            showBanner('Запись удалена.', false);
            await loadHistory({ silent: true });
        }
    }

    document.getElementById('btn-load-history').addEventListener('click', loadHistory);
    document.getElementById('field-select')?.addEventListener('change', () => {
        const fieldId = parseInt(document.getElementById('field-select').value, 10);
        if (fieldId) {
            loadHistory();
        } else {
            hideFieldWorkPanel();
        }
    });
    document.getElementById('btn-mark-obsolete')?.addEventListener('click', markFieldObsolete);
    document.getElementById('btn-restore-field')?.addEventListener('click', restoreObsoleteField);
    document.getElementById('btn-save-record')?.addEventListener('click', saveRecord);
    document.getElementById('btn-cancel-edit')?.addEventListener('click', resetFormMode);
    document.getElementById('btn-new-record')?.addEventListener('click', resetFormMode);

    loadSummary().catch(() => showBanner('Ошибка загрузки севооборота.', true));
})();