/**
 * Страница агронома: выбор поля и правка истории посевов (field_crops).
 */
(function () {
    const API = '/api/org/agronomist';
    const fetchOpts = { credentials: 'same-origin' };

    let lastCsrf = null;
    let summaryData = null;
    let selectedFieldId = null;
    /** Последний ответ GET history — для кнопки «Изменить» без кривого JSON в DOM */
    let lastHistoryRows = [];
    /** null — режим добавления; иначе id строки для PUT */
    let editingHistoryId = null;

    function mutatingHeaders() {
        const h = csrfHeaders({ 'Content-Type': 'application/json' });
        const headerName = lastCsrf && lastCsrf.headerName ? lastCsrf.headerName : 'X-XSRF-TOKEN';
        const token = (lastCsrf && lastCsrf.token) ? lastCsrf.token : readXsrfToken();
        if (token) {
            h[headerName] = token;
        }
        return h;
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

    function numOrNull(id) {
        const el = document.getElementById(id);
        const v = el.value.trim();
        if (v === '') return null;
        const n = Number(v);
        return Number.isFinite(n) ? n : null;
    }

    function dateOrNull(id) {
        const v = document.getElementById(id).value.trim();
        return v === '' ? null : v;
    }

    function strOrNull(id) {
        const v = document.getElementById(id).value.trim();
        return v === '' ? null : v;
    }

    function buildPayload() {
        const cropSel = document.getElementById('form-crop');
        const cropId = parseInt(cropSel.value, 10);
        const year = parseInt(document.getElementById('form-year').value, 10);
        if (!cropId || !Number.isFinite(year)) {
            return null;
        }
        return {
            cropId: cropId,
            cropYear: year,
            sowingDate: dateOrNull('form-sowing'),
            harvestDate: dateOrNull('form-harvest'),
            sownAreaHa: numOrNull('form-sown-ha'),
            harvestAreaHa: numOrNull('form-harvest-ha'),
            actualYield: numOrNull('form-actual-yield'),
            totalYield: numOrNull('form-total-yield'),
            plannedYield: numOrNull('form-planned-yield'),
            forecastedYield: numOrNull('form-forecast-yield'),
            sourceData: strOrNull('form-source'),
            sowingDetails: strOrNull('form-details'),
        };
    }

    function fillCropSelect(crops) {
        const sel = document.getElementById('form-crop');
        sel.innerHTML = crops.map(function (c) {
            return '<option value="' + c.cropId + '">' + escapeHtml(c.cropName) + '</option>';
        }).join('');
    }

    function fillFieldSelect(fields) {
        const sel = document.getElementById('field-select');
        const keep = sel.value;
        sel.innerHTML = '<option value="">— выберите поле —</option>' +
            fields.map(function (f) {
                return '<option value="' + f.fieldId + '">' + escapeHtml(f.fieldName) + '</option>';
            }).join('');
        if (keep && fields.some(function (f) { return String(f.fieldId) === keep; })) {
            sel.value = keep;
        }
    }

    function clearForm() {
        editingHistoryId = null;
        document.getElementById('form-title').textContent = 'Новая запись';
        document.getElementById('form-year').value = '';
        document.getElementById('form-sowing').value = '';
        document.getElementById('form-harvest').value = '';
        ['form-sown-ha', 'form-harvest-ha', 'form-actual-yield', 'form-total-yield', 'form-planned-yield', 'form-forecast-yield', 'form-source', 'form-details']
            .forEach(function (id) { document.getElementById(id).value = ''; });
    }

    function enterEditMode(row) {
        editingHistoryId = row.historyId;
        document.getElementById('form-title').textContent = 'Редактирование записи #' + row.historyId;
        document.getElementById('form-crop').value = String(row.cropId);
        document.getElementById('form-year').value = row.cropYear != null ? String(row.cropYear) : '';
        document.getElementById('form-sowing').value = row.sowingDate || '';
        document.getElementById('form-harvest').value = row.harvestDate || '';
        document.getElementById('form-sown-ha').value = row.sownAreaHa != null ? String(row.sownAreaHa) : '';
        document.getElementById('form-harvest-ha').value = row.harvestAreaHa != null ? String(row.harvestAreaHa) : '';
        document.getElementById('form-actual-yield').value = row.actualYield != null ? String(row.actualYield) : '';
        document.getElementById('form-total-yield').value = row.totalYield != null ? String(row.totalYield) : '';
        document.getElementById('form-planned-yield').value = row.plannedYield != null ? String(row.plannedYield) : '';
        document.getElementById('form-forecast-yield').value = row.forecastedYield != null ? String(row.forecastedYield) : '';
        document.getElementById('form-source').value = row.sourceData || '';
        document.getElementById('form-details').value = row.sowingDetails || '';
        document.getElementById('form-card').style.display = 'block';
    }

    /** Открытие с карты: /org/agronomist?fieldId=… — выбрать поле и сразу показать историю. */
    async function applyFieldIdFromQuery() {
        const params = new URLSearchParams(window.location.search);
        const raw = params.get('fieldId');
        if (!raw) {
            return;
        }
        const fid = parseInt(raw, 10);
        if (!Number.isFinite(fid) || fid <= 0) {
            return;
        }
        const sel = document.getElementById('field-select');
        const hasOption = Array.from(sel.options).some(function (o) {
            return o.value === String(fid);
        });
        if (!hasOption) {
            showBanner('Поле #' + fid + ' не в списке: для него нет записей истории в вашей организации или указан неверный id.', true);
            return;
        }
        sel.value = String(fid);
        await loadHistory();
    }

    async function loadSummary() {
        showBanner('', false);
        const res = await fetch(API + '/summary', fetchOpts);
        if (!res.ok) {
            showBanner('Не удалось загрузить данные (' + res.status + ').', true);
            return;
        }
        summaryData = await res.json();
        lastCsrf = summaryData.csrf || null;
        document.getElementById('org-name').textContent = (summaryData.organizationName && String(summaryData.organizationName).trim() !== '')
            ? summaryData.organizationName
            : ('#' + String(summaryData.organizationId));
        const ul = document.getElementById('user-label');
        if (ul) {
            ul.textContent = 'Вы: ' + (summaryData.currentUserFullName || summaryData.currentLogin || '');
        }
        fillFieldSelect(summaryData.fields || []);
        fillCropSelect(summaryData.crops || []);
        if (!summaryData.fields || summaryData.fields.length === 0) {
            showBanner('Нет полей с историей посевов. Сначала должна существовать хотя бы одна запись field_crops для поля организации.', true);
        } else {
            await applyFieldIdFromQuery();
        }
    }

    async function loadHistory() {
        const sel = document.getElementById('field-select');
        const fieldId = parseInt(sel.value, 10);
        if (!fieldId) {
            showBanner('Выберите поле из списка.', true);
            return;
        }
        selectedFieldId = fieldId;
        showBanner('', false);
        const res = await fetch(API + '/fields/' + fieldId + '/history', fetchOpts);
        if (!res.ok) {
            showBanner('Не удалось загрузить историю (' + res.status + ').', true);
            return;
        }
        const rows = await res.json();
        lastHistoryRows = rows;
        const hw = document.getElementById('history-wrap');
        if (!rows.length) {
            hw.innerHTML = '<p>Записей нет.</p>';
        } else {
            const head = '<thead><tr><th>Год</th><th>Культура</th><th>Посев</th><th>Уборка</th><th>Засеяно, га</th><th>Урож. (факт)</th><th></th></tr></thead>';
            const body = rows.map(function (r) {
                return '<tr data-hid="' + r.historyId + '">' +
                    '<td>' + escapeHtml(r.cropYear != null ? String(r.cropYear) : '—') + '</td>' +
                    '<td>' + escapeHtml(r.cropName || '') + '</td>' +
                    '<td>' + escapeHtml(r.sowingDate || '—') + '</td>' +
                    '<td>' + escapeHtml(r.harvestDate || '—') + '</td>' +
                    '<td>' + escapeHtml(r.sownAreaHa != null ? String(r.sownAreaHa) : '—') + '</td>' +
                    '<td>' + escapeHtml(r.actualYield != null ? String(r.actualYield) : '—') + '</td>' +
                    '<td><button type="button" data-act="edit">Изменить</button> ' +
                    '<button type="button" class="danger" data-act="del" data-id="' + r.historyId + '">Удалить</button></td></tr>';
            }).join('');
            hw.innerHTML = '<table>' + head + '<tbody>' + body + '</tbody></table>';
        }
        document.getElementById('history-card').style.display = 'block';
        document.getElementById('form-card').style.display = 'block';
        clearForm();

        hw.querySelectorAll('button[data-act="edit"]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                const tr = btn.closest('tr');
                const hid = parseInt(tr.getAttribute('data-hid'), 10);
                const row = lastHistoryRows.find(function (x) { return x.historyId === hid; });
                if (row) enterEditMode(row);
            });
        });
        hw.querySelectorAll('button[data-act="del"]').forEach(function (btn) {
            btn.addEventListener('click', async function () {
                const id = parseInt(btn.getAttribute('data-id'), 10);
                if (!confirm('Удалить запись #' + id + '?')) return;
                const res = await fetch(API + '/history/' + id, {
                    method: 'DELETE',
                    credentials: 'same-origin',
                    headers: mutatingHeaders(),
                });
                const text = await res.text();
                let payload = null;
                try { payload = text ? JSON.parse(text) : null; } catch (_) { /* empty */ }
                if (!res.ok) {
                    showBanner('Ошибка ' + res.status + (payload && payload.message ? ': ' + payload.message : ''), true);
                    return;
                }
                if (payload && payload.message) showBanner(payload.message, false);
                await loadHistory();
                await loadSummary();
            });
        });
    }

    document.getElementById('btn-load-history').addEventListener('click', function () {
        loadHistory().catch(function () {
            showBanner('Ошибка сети.', true);
        });
    });

    document.getElementById('btn-cancel-edit').addEventListener('click', function () {
        clearForm();
    });

    document.getElementById('btn-new-record').addEventListener('click', function () {
        clearForm();
    });

    document.getElementById('btn-save-record').addEventListener('click', async function () {
        const body = buildPayload();
        if (!body) {
            showBanner('Укажите культуру и год поля (crop_year).', true);
            return;
        }
        if (!selectedFieldId) {
            showBanner('Сначала выберите поле и нажмите «Показать историю».', true);
            return;
        }
        const url = editingHistoryId != null
            ? API + '/history/' + editingHistoryId
            : API + '/fields/' + selectedFieldId + '/history';
        const method = editingHistoryId != null ? 'PUT' : 'POST';
        const res = await fetch(url, {
            method: method,
            credentials: 'same-origin',
            headers: mutatingHeaders(),
            body: JSON.stringify(body),
        });
        const text = await res.text();
        let payload = null;
        try { payload = text ? JSON.parse(text) : null; } catch (_) { /* empty */ }
        if (!res.ok) {
            showBanner('Ошибка ' + res.status + (payload && payload.message ? ': ' + payload.message : ''), true);
            return;
        }
        if (payload && payload.message) showBanner(payload.message, false);
        clearForm();
        await loadHistory();
        await loadSummary();
    });

    loadSummary().catch(function () {
        showBanner('Ошибка сети при загрузке страницы.', true);
    });
})();
