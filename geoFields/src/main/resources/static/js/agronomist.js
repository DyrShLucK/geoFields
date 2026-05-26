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
        if (ul) ul.textContent = 'Вы: ' + (summaryData.currentUserFullName || summaryData.currentLogin || '');

        // Заполнение селектов полей
        fillFieldSelects();
    }

    function fillFieldSelects() {
        const fs = document.getElementById('field-select');
        const ofs = document.getElementById('obsolete-field-select');
        const cs = document.getElementById('form-crop');

        fs.innerHTML = '<option value="">— Выбрать активное поле —</option>' +
            (summaryData.fields || []).map(f => `<option value="${f.fieldId}">${escapeHtml(f.fieldName)}</option>`).join('');

        ofs.innerHTML = '<option value="">— Архивные поля отсутствуют —</option>' +
            (summaryData.obsoleteFields || []).map(f => `<option value="${f.fieldId}">${escapeHtml(f.fieldName)}</option>`).join('');

        cs.innerHTML = (summaryData.crops || []).map(c => `<option value="${c.cropId}">${escapeHtml(c.cropName)}</option>`).join('');
    }

    async function loadHistory() {
        const fieldId = parseInt(document.getElementById('field-select').value, 10);
        if (!fieldId) return;

        selectedFieldId = fieldId;
        const res = await fetch(`${API}/fields/${fieldId}/history`, fetchOpts);
        if (!res.ok) return;

        const rows = await res.json();
        lastHistoryRows = rows;

        const hw = document.getElementById('history-wrap');
        if (!rows.length) {
            hw.innerHTML = '<p class="muted">Данные по севу не найдены.</p>';
        } else {
            let trs = rows.map(r => `
                <tr>
                    <td>${r.cropYear || '—'}</td>
                    <td>${escapeHtml(r.cropName)}</td>
                    <td>${r.sowingDate || '—'}</td>
                    <td>${r.harvestDate || '—'}</td>
                    <td>${r.sownAreaHa || '—'}</td>
                    <td>${r.actualYield || '—'}</td>
                    <td>
                        <button type="button" class="btn primary mr-2" data-act="edit" data-id="${r.historyId}">Редактировать</button>
                        <button type="button" class="btn danger" data-act="del" data-id="${r.historyId}">Удалить</button>
                    </td>
                </tr>
            `).join('');
            hw.innerHTML = `<table><thead><tr><th>Год</th><th>Культура</th><th>Сев</th><th>Жатва</th><th>Площадь</th><th>Сбор</th><th></th></tr></thead><tbody>${trs}</tbody></table>`;

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
        document.getElementById('history-card').style.display = 'block';
        document.getElementById('form-card').style.display = 'block';
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
            await loadHistory();
        }
    }

    document.getElementById('btn-load-history').addEventListener('click', loadHistory);

    loadSummary().catch(() => showBanner('Ошибка загрузки севооборота.', true));
})();