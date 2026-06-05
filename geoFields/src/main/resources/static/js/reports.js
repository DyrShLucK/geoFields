/**
 * Отчёты и аналитика — /org/reports (API /api/reports).
 */
(function () {
    const API = '/api/reports';
    const SUMMARY_API = '/api/org/agronomist';
    const fetchOpts = { credentials: 'same-origin' };

    const STATUS_COLORS = {
        PLANNED: '#94a3b8',
        STARTED: '#60a5fa',
        IN_PROGRESS: '#3b82f6',
        COMPLETED: '#10b981',
        FROZEN: '#0ea5e9',
        CANCELLED: '#ef4444'
    };
    const CHART_PALETTE = ['#10b981', '#3b82f6', '#f59e0b', '#8b5cf6', '#ec4899',
        '#14b8a6', '#f97316', '#6366f1', '#84cc16', '#ef4444'];

    const LAYOUT_STORAGE_KEY = 'geofields-reports-layout-v2';
    const BLOCK_CATALOG = [
        { id: 'kpis', title: 'Ключевые показатели' },
        { id: 'timeline', title: 'Динамика операций по месяцам' },
        { id: 'status', title: 'Операции по статусам' },
        { id: 'category', title: 'Операции по категориям' },
        { id: 'crops-area', title: 'Площади по культурам' },
        { id: 'crops-yield', title: 'Урожайность по культурам' },
        { id: 'fields-table', title: 'Сводка по полям' },
        { id: 'ops-table', title: 'Операции в периоде' }
    ];
    const DEFAULT_BLOCK_ORDER = BLOCK_CATALOG.map(b => b.id);

    let summaryData = null;
    let lastDashboard = null;
    const fieldsSort = { key: 'fieldName', dir: 1 };
    const opsSort = { key: 'operationAt', dir: -1 };
    let layoutSortable = null;
    let layoutEditMode = false;
    let layoutState = null;

    function escapeHtml(s) {
        if (s == null) return '';
        return String(s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    function showBanner(text, isError) {
        const el = document.getElementById('banner');
        if (!el) return;
        el.innerHTML = text ? '<div class="msg ' + (isError ? 'err' : 'ok') + '">' + escapeHtml(text) + '</div>' : '';
    }

    function formatNum(n, digits) {
        if (n == null || Number.isNaN(n)) return '—';
        return Number(n).toLocaleString('ru-RU', { minimumFractionDigits: digits, maximumFractionDigits: digits });
    }

    function formatDate(iso) {
        if (!iso) return '—';
        const d = new Date(iso);
        if (Number.isNaN(d.getTime())) return String(iso);
        return d.toLocaleString('ru-RU', {
            year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
        });
    }

    function padDate(d) {
        const p = n => String(n).padStart(2, '0');
        return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
    }

    function setDefaultPeriod(months) {
        const to = new Date();
        const from = new Date();
        if (months > 0) from.setMonth(from.getMonth() - months);
        else from.setFullYear(2020, 0, 1);
        document.getElementById('filter-date-from').value = padDate(from);
        document.getElementById('filter-date-to').value = padDate(to);
    }

    function getSelectedFieldIds() {
        return Array.from(document.querySelectorAll('#fields-checklist input[type="checkbox"]:checked'))
            .map(cb => cb.value).filter(Boolean);
    }

    function updateScopeHint() {
        const hint = document.getElementById('report-scope-hint');
        if (!hint) return;
        const selected = getSelectedFieldIds();
        const total = summaryData?.fields?.length || 0;
        hint.textContent = !selected.length
            ? (total ? `Будут учтены все активные поля (${total})` : '')
            : `Выбрано полей: ${selected.length} из ${total}`;
    }

    function renderFieldsChecklist(filterText) {
        const wrap = document.getElementById('fields-checklist');
        const fields = summaryData?.fields || [];
        if (!fields.length) {
            wrap.innerHTML = '<p class="muted font-sm m-0">Нет полей в организации.</p>';
            return;
        }
        const prevChecked = new Set(getSelectedFieldIds());
        const initial = !wrap.dataset.ready;
        const q = (filterText || '').trim().toLowerCase();
        const shown = fields.filter(f => !q || String(f.fieldName).toLowerCase().includes(q));
        if (!shown.length) {
            wrap.innerHTML = '<p class="muted font-sm m-0">Поля не найдены.</p>';
            return;
        }
        wrap.innerHTML = shown.map(f => {
            const checked = initial || prevChecked.has(String(f.fieldId));
            return `<label class="reports-field-chip">
                <input type="checkbox" value="${f.fieldId}"${checked ? ' checked' : ''}>
                <span>${escapeHtml(f.fieldName)}</span>
            </label>`;
        }).join('');
        wrap.dataset.ready = '1';
        wrap.querySelectorAll('input').forEach(cb => cb.addEventListener('change', updateScopeHint));
        updateScopeHint();
    }

    async function loadSummary() {
        const res = await fetch(SUMMARY_API + '/summary', fetchOpts);
        if (!res.ok) {
            showBanner('Не удалось загрузить данные организации.', true);
            return;
        }
        summaryData = await res.json();
        document.getElementById('org-name').textContent =
            summaryData.organizationName || ('#' + summaryData.organizationId);
        const ul = document.getElementById('user-label');
        if (ul) ul.textContent = summaryData.currentUserFullName || summaryData.currentLogin || '';
        renderFieldsChecklist();
    }

    // ---------- KPI ----------
    function buildKpiCards(overview) {
        const completionPct = overview.operationsTotal > 0
            ? Math.round((overview.operationsCompleted / overview.operationsTotal) * 100) : 0;
        return [
            { id: 'fieldsInScope', label: 'Полей в отчёте', value: overview.fieldsInScope, icon: '🌾' },
            { id: 'totalAreaHa', label: 'Суммарная площадь', value: formatNum(overview.totalAreaHa, 1), suffix: ' га', icon: '📐' },
            { id: 'operationsTotal', label: 'Операций за период', value: overview.operationsTotal, icon: '🚜' },
            { id: 'operationsCompleted', label: 'Завершено', value: overview.operationsCompleted, suffix: ` (${completionPct}%)`, icon: '✅', good: true },
            { id: 'operationsInProgress', label: 'В работе', value: overview.operationsInProgress, icon: '⏳' },
            { id: 'operationsPlanned', label: 'Запланировано', value: overview.operationsPlanned, icon: '📅' },
            { id: 'overduePlanned', label: 'Просрочено (план)', value: overview.overduePlanned, icon: '⚠️', warn: overview.overduePlanned > 0 },
            { id: 'avgActualYield', label: 'Средняя урожайность', value: overview.avgActualYield != null ? formatNum(overview.avgActualYield, 2) : '—', suffix: overview.avgActualYield != null ? ' ц/га' : '', icon: '📊' }
        ];
    }

    function isKpiEnabled(kpiId) {
        return layoutState?.kpis?.[kpiId] !== false;
    }

    function renderKpis(overview) {
        const grid = document.getElementById('kpi-grid');
        const cards = buildKpiCards(overview).filter(c => isKpiEnabled(c.id));
        if (!cards.length) {
            grid.innerHTML = '<p class="muted font-sm m-0">Нет выбранных показателей. Включите их в настройке блоков.</p>';
            return;
        }
        grid.innerHTML = cards.map(c => `
            <div class="reports-kpi-card${c.warn ? ' reports-kpi-card--warn' : ''}${c.good ? ' reports-kpi-card--good' : ''}" data-kpi-id="${c.id}">
                <span class="reports-kpi-icon">${c.icon}</span>
                <span class="reports-kpi-label">${escapeHtml(c.label)}</span>
                <strong class="reports-kpi-value">${escapeHtml(String(c.value))}<span class="reports-kpi-suffix">${escapeHtml(c.suffix || '')}</span></strong>
            </div>
        `).join('');
    }

    // ---------- Bar chart (colored) ----------
    function renderBarChart(containerId, items, emptyText, opts) {
        const el = document.getElementById(containerId);
        if (!items.length) {
            el.innerHTML = `<p class="muted font-sm m-0">${escapeHtml(emptyText)}</p>`;
            return;
        }
        const max = Math.max(...items.map(i => i.count), 1);
        el.innerHTML = items.map((item, i) => {
            const pct = Math.max(2, Math.round((item.count / max) * 100));
            const color = (opts && opts.colorFor) ? opts.colorFor(item, i) : CHART_PALETTE[i % CHART_PALETTE.length];
            const valLabel = (opts && opts.format) ? opts.format(item.count) : item.count;
            return `
                <div class="reports-bar-row" title="${escapeHtml(item.label)}: ${valLabel}">
                    <span class="reports-bar-label">${escapeHtml(item.label)}</span>
                    <div class="reports-bar-track">
                        <div class="reports-bar-fill" style="width:${pct}%;background:${color}"></div>
                    </div>
                    <span class="reports-bar-value">${escapeHtml(String(valLabel))}</span>
                </div>`;
        }).join('');
    }

    // ---------- Donut chart (SVG) ----------
    function renderDonut(containerId, items, emptyText) {
        const el = document.getElementById(containerId);
        if (!items.length) {
            el.innerHTML = `<p class="muted font-sm m-0">${escapeHtml(emptyText)}</p>`;
            return;
        }
        const total = items.reduce((s, i) => s + i.count, 0);
        if (total === 0) {
            el.innerHTML = `<p class="muted font-sm m-0">${escapeHtml(emptyText)}</p>`;
            return;
        }
        const size = 168, stroke = 26;
        const r = (size - stroke) / 2, cx = size / 2, cy = size / 2;
        const circ = 2 * Math.PI * r;
        let offset = 0;
        const segments = items.map((item, i) => {
            const frac = item.count / total;
            const color = STATUS_COLORS[item.code] || CHART_PALETTE[i % CHART_PALETTE.length];
            const dash = `${(frac * circ).toFixed(2)} ${(circ - frac * circ).toFixed(2)}`;
            const dashOffset = (-offset * circ).toFixed(2);
            offset += frac;
            return `<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="${color}"
                stroke-width="${stroke}" stroke-dasharray="${dash}" stroke-dashoffset="${dashOffset}"
                transform="rotate(-90 ${cx} ${cy})">
                <title>${escapeHtml(item.label)}: ${item.count} (${Math.round(frac * 100)}%)</title>
            </circle>`;
        }).join('');
        const legend = items.map((item, i) => {
            const color = STATUS_COLORS[item.code] || CHART_PALETTE[i % CHART_PALETTE.length];
            const pct = Math.round((item.count / total) * 100);
            return `<div class="reports-donut-legend-item">
                <span class="reports-donut-dot" style="background:${color}"></span>
                <span class="reports-donut-legend-label">${escapeHtml(item.label)}</span>
                <span class="reports-donut-legend-val">${item.count} · ${pct}%</span>
            </div>`;
        }).join('');
        el.innerHTML = `
            <div class="reports-donut-chart">
                <svg viewBox="0 0 ${size} ${size}" width="${size}" height="${size}">
                    ${segments}
                    <text x="${cx}" y="${cy - 4}" text-anchor="middle" class="reports-donut-total">${total}</text>
                    <text x="${cx}" y="${cy + 16}" text-anchor="middle" class="reports-donut-total-label">операций</text>
                </svg>
            </div>
            <div class="reports-donut-legend">${legend}</div>`;
    }

    // ---------- Timeline (SVG area + line) ----------
    function renderTimeline(items) {
        const el = document.getElementById('chart-timeline');
        const sub = document.getElementById('timeline-sub');
        if (!items.length) {
            el.innerHTML = '<p class="muted font-sm m-0">Нет операций за период.</p>';
            if (sub) sub.textContent = '';
            return;
        }
        const total = items.reduce((s, i) => s + i.count, 0);
        if (sub) sub.textContent = `всего ${total}`;
        const max = Math.max(...items.map(i => i.count), 1);
        const W = 760, H = 240, padL = 36, padR = 16, padT = 16, padB = 36;
        const plotW = W - padL - padR, plotH = H - padT - padB;
        const n = items.length;
        const xFor = i => padL + (n === 1 ? plotW / 2 : (i / (n - 1)) * plotW);
        const yFor = v => padT + plotH - (v / max) * plotH;

        const gridLines = [0, 0.25, 0.5, 0.75, 1].map(f => {
            const y = padT + plotH - f * plotH;
            const val = Math.round(max * f);
            return `<line x1="${padL}" y1="${y}" x2="${W - padR}" y2="${y}" class="reports-tl-grid"/>
                <text x="${padL - 6}" y="${y + 3}" text-anchor="end" class="reports-tl-axis">${val}</text>`;
        }).join('');

        const linePts = items.map((it, i) => `${xFor(i)},${yFor(it.count)}`).join(' ');
        const areaPts = `${padL},${padT + plotH} ${linePts} ${xFor(n - 1)},${padT + plotH}`;

        const dots = items.map((it, i) => {
            const label = it.period ? it.period.replace('-', '.') : '';
            return `<g class="reports-tl-point">
                <circle cx="${xFor(i)}" cy="${yFor(it.count)}" r="4" class="reports-tl-dot"/>
                <circle cx="${xFor(i)}" cy="${yFor(it.count)}" r="11" fill="transparent">
                    <title>${escapeHtml(label)}: ${it.count}</title>
                </circle>
            </g>`;
        }).join('');

        const step = Math.ceil(n / 12);
        const xLabels = items.map((it, i) => {
            if (i % step !== 0 && i !== n - 1) return '';
            const label = it.period ? it.period.replace('-', '.') : '';
            return `<text x="${xFor(i)}" y="${H - 10}" text-anchor="middle" class="reports-tl-axis">${escapeHtml(label)}</text>`;
        }).join('');

        el.innerHTML = `
            <svg viewBox="0 0 ${W} ${H}" width="100%" preserveAspectRatio="xMidYMid meet" class="reports-tl-svg">
                <defs>
                    <linearGradient id="tlGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stop-color="#3b82f6" stop-opacity="0.32"/>
                        <stop offset="100%" stop-color="#3b82f6" stop-opacity="0"/>
                    </linearGradient>
                </defs>
                ${gridLines}
                <polygon points="${areaPts}" fill="url(#tlGrad)"/>
                <polyline points="${linePts}" fill="none" stroke="#3b82f6" stroke-width="2.5"
                    stroke-linejoin="round" stroke-linecap="round"/>
                ${dots}
                ${xLabels}
            </svg>`;
    }

    function renderCropCharts(crops) {
        if (!crops.length) {
            document.getElementById('chart-crops').innerHTML = '<p class="muted font-sm m-0">Нет данных по культурам.</p>';
            document.getElementById('chart-yield').innerHTML = '<p class="muted font-sm m-0">Нет данных по урожайности.</p>';
            return;
        }
        const areaItems = crops.filter(c => c.totalAreaHa != null)
            .map(c => ({ label: c.cropName, count: Math.round((c.totalAreaHa || 0) * 10) / 10 }));
        renderBarChart('chart-crops', areaItems, 'Нет площадей', { format: v => formatNum(v, 1) });

        const yieldItems = crops.filter(c => c.avgYield != null)
            .map(c => ({ label: c.cropName, count: Math.round((c.avgYield || 0) * 100) / 100 }));
        renderBarChart('chart-yield', yieldItems, 'Нет урожайности', {
            colorFor: () => '#f59e0b', format: v => formatNum(v, 2)
        });
    }

    // ---------- Sortable + filtered tables ----------
    function sortRows(rows, key, dir, type) {
        const copy = [...rows];
        copy.sort((a, b) => {
            let va = a[key], vb = b[key];
            if (va == null && vb == null) return 0;
            if (va == null) return 1;
            if (vb == null) return -1;
            if (type === 'num') return (va - vb) * dir;
            if (type === 'date') return (new Date(va) - new Date(vb)) * dir;
            return String(va).localeCompare(String(vb), 'ru') * dir;
        });
        return copy;
    }

    function updateSortIndicators(tableId, sortState) {
        document.querySelectorAll(`#${tableId} thead th[data-sort]`).forEach(th => {
            th.classList.remove('is-sorted-asc', 'is-sorted-desc');
            if (th.getAttribute('data-sort') === sortState.key) {
                th.classList.add(sortState.dir === 1 ? 'is-sorted-asc' : 'is-sorted-desc');
            }
        });
    }

    function getFieldsRows() {
        const q = (document.getElementById('fields-table-search')?.value || '').trim().toLowerCase();
        let rows = (lastDashboard?.fieldSummaries || []);
        if (q) {
            rows = rows.filter(r =>
                String(r.fieldName || '').toLowerCase().includes(q) ||
                String(r.latestCropName || '').toLowerCase().includes(q));
        }
        const th = document.querySelector(`#fields-table th[data-sort="${fieldsSort.key}"]`);
        return sortRows(rows, fieldsSort.key, fieldsSort.dir, th?.getAttribute('data-type') || 'str');
    }

    function renderFieldsTable() {
        const tbody = document.querySelector('#fields-table tbody');
        const rows = getFieldsRows();
        if (!rows.length) {
            tbody.innerHTML = '<tr><td colspan="7" class="history-empty">Нет данных по фильтру</td></tr>';
            return;
        }
        tbody.innerHTML = rows.map(r => `
            <tr>
                <td><strong>${escapeHtml(r.fieldName)}</strong></td>
                <td>${r.areaHa != null ? formatNum(r.areaHa, 1) : '—'}</td>
                <td>${r.operationsCount}</td>
                <td><span class="reports-progress-cell">${r.completedOperations}/${r.operationsCount}
                    <span class="reports-mini-track"><span class="reports-mini-fill" style="width:${r.operationsCount ? Math.round(r.completedOperations / r.operationsCount * 100) : 0}%"></span></span></span></td>
                <td>${escapeHtml(r.latestCropName || '—')}</td>
                <td>${r.latestCropYear != null ? r.latestCropYear : '—'}</td>
                <td>${r.latestYield != null ? formatNum(r.latestYield, 2) : '—'}</td>
            </tr>`).join('');
        updateSortIndicators('fields-table', fieldsSort);
    }

    function getOpsRows() {
        const q = (document.getElementById('ops-table-search')?.value || '').trim().toLowerCase();
        const st = document.getElementById('ops-table-status')?.value || '';
        const cat = document.getElementById('ops-table-category')?.value || '';
        let rows = (lastDashboard?.recentOperations || []);
        if (q) rows = rows.filter(r =>
            String(r.name || '').toLowerCase().includes(q) ||
            String(r.fieldName || '').toLowerCase().includes(q));
        if (st) rows = rows.filter(r => r.statusTitleRu === st);
        if (cat) rows = rows.filter(r => r.categoryTitleRu === cat);
        const th = document.querySelector(`#recent-ops-table th[data-sort="${opsSort.key}"]`);
        return sortRows(rows, opsSort.key, opsSort.dir, th?.getAttribute('data-type') || 'str');
    }

    function renderOpsTable() {
        const tbody = document.querySelector('#recent-ops-table tbody');
        const rows = getOpsRows();
        if (!rows.length) {
            tbody.innerHTML = '<tr><td colspan="5" class="history-empty">Нет операций по фильтру</td></tr>';
            return;
        }
        tbody.innerHTML = rows.map(r => `
            <tr>
                <td>${formatDate(r.operationAt)}</td>
                <td>${escapeHtml(r.fieldName)}</td>
                <td>${escapeHtml(r.name)}</td>
                <td>${escapeHtml(r.categoryTitleRu)}</td>
                <td><span class="reports-status-pill" data-st="${escapeHtml(r.statusTitleRu)}">${escapeHtml(r.statusTitleRu)}</span></td>
            </tr>`).join('');
        updateSortIndicators('recent-ops-table', opsSort);
    }

    function fillOpsTableFilters() {
        const rows = lastDashboard?.recentOperations || [];
        const statuses = [...new Set(rows.map(r => r.statusTitleRu).filter(Boolean))].sort();
        const cats = [...new Set(rows.map(r => r.categoryTitleRu).filter(Boolean))].sort();
        const stSel = document.getElementById('ops-table-status');
        const catSel = document.getElementById('ops-table-category');
        if (stSel) stSel.innerHTML = '<option value="">Все статусы</option>' +
            statuses.map(s => `<option value="${escapeHtml(s)}">${escapeHtml(s)}</option>`).join('');
        if (catSel) catSel.innerHTML = '<option value="">Все категории</option>' +
            cats.map(c => `<option value="${escapeHtml(c)}">${escapeHtml(c)}</option>`).join('');
    }

    // ---------- CSV export ----------
    function downloadCsv(filename, headers, rows) {
        const esc = v => {
            const s = v == null ? '' : String(v);
            return /[",;\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
        };
        const csv = [headers.map(esc).join(';')]
            .concat(rows.map(r => r.map(esc).join(';'))).join('\r\n');
        const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
    }

    function exportFields() {
        const rows = getFieldsRows();
        downloadCsv('fields-report.csv',
            ['Поле', 'Площадь, га', 'Операций', 'Завершено', 'Последняя культура', 'Год', 'Урожайность'],
            rows.map(r => [r.fieldName, r.areaHa, r.operationsCount, r.completedOperations,
                r.latestCropName, r.latestCropYear, r.latestYield]));
    }

    function exportOps() {
        const rows = getOpsRows();
        downloadCsv('operations-report.csv',
            ['Дата', 'Поле', 'Операция', 'Категория', 'Статус'],
            rows.map(r => [formatDate(r.operationAt), r.fieldName, r.name, r.categoryTitleRu, r.statusTitleRu]));
    }

    // ---------- Layout constructor (vertical stack + Sortable) ----------
    function defaultLayoutState() {
        const blocks = {};
        BLOCK_CATALOG.forEach(b => { blocks[b.id] = true; });
        const kpis = {};
        buildKpiCards({
            fieldsInScope: 0, totalAreaHa: 0, operationsTotal: 0, operationsCompleted: 0,
            operationsInProgress: 0, operationsPlanned: 0, overduePlanned: 0, avgActualYield: null
        }).forEach(k => { kpis[k.id] = true; });
        return { blocks, kpis, order: [...DEFAULT_BLOCK_ORDER] };
    }

    function normalizeOrder(order) {
        const seen = new Set();
        const result = [];
        (order || []).forEach(id => {
            if (DEFAULT_BLOCK_ORDER.includes(id) && !seen.has(id)) {
                seen.add(id);
                result.push(id);
            }
        });
        DEFAULT_BLOCK_ORDER.forEach(id => {
            if (!seen.has(id)) result.push(id);
        });
        return result;
    }

    function loadLayoutState() {
        try {
            const raw = localStorage.getItem(LAYOUT_STORAGE_KEY)
                || localStorage.getItem('geofields-reports-layout-v1');
            if (!raw) return defaultLayoutState();
            const parsed = JSON.parse(raw);
            const base = defaultLayoutState();
            if (parsed.blocks) Object.assign(base.blocks, parsed.blocks);
            if (parsed.kpis) Object.assign(base.kpis, parsed.kpis);
            if (Array.isArray(parsed.order) && parsed.order.length) {
                base.order = normalizeOrder(parsed.order);
            } else if (Array.isArray(parsed.items) && parsed.items.length) {
                base.order = normalizeOrder(
                    [...parsed.items].sort((a, b) => (a.y || 0) - (b.y || 0)).map(i => i.id)
                );
            }
            return base;
        } catch (_e) {
            return defaultLayoutState();
        }
    }

    function saveLayoutState() {
        try {
            localStorage.setItem(LAYOUT_STORAGE_KEY, JSON.stringify(layoutState));
        } catch (_e) { /* ignore quota */ }
    }

    function isBlockEnabled(blockId) {
        return layoutState?.blocks?.[blockId] !== false;
    }

    function getBlockEl(blockId) {
        return document.querySelector(`#reports-layout-grid .reports-layout-item[data-block-id="${blockId}"]`);
    }

    function syncOrderFromDom() {
        layoutState.order = Array.from(
            document.querySelectorAll('#reports-layout-grid .reports-layout-item')
        ).map(el => el.getAttribute('data-block-id')).filter(Boolean);
        layoutState.order = normalizeOrder(layoutState.order);
    }

    function applyBlockOrder() {
        const stack = document.getElementById('reports-layout-grid');
        if (!stack) return;
        const order = normalizeOrder(layoutState?.order);
        order.forEach(id => {
            const el = getBlockEl(id);
            if (el) stack.appendChild(el);
        });
    }

    function applyBlockVisibility() {
        BLOCK_CATALOG.forEach(b => {
            const el = getBlockEl(b.id);
            if (!el) return;
            el.classList.toggle('reports-block--hidden', !isBlockEnabled(b.id));
        });
    }

    function setLayoutEditMode(on) {
        layoutEditMode = !!on;
        const dash = document.getElementById('dashboard');
        const hint = document.getElementById('layout-hint');
        const panel = document.getElementById('blocks-panel');
        const editBtn = document.getElementById('btn-layout-edit');
        if (dash) dash.classList.toggle('is-layout-edit', layoutEditMode);
        if (hint) hint.hidden = !layoutEditMode;
        if (panel) panel.hidden = !layoutEditMode;
        if (editBtn) editBtn.textContent = layoutEditMode ? '⚙ Редактирование…' : '⚙ Настроить блоки';
        if (layoutSortable) layoutSortable.option('disabled', !layoutEditMode);
        renderBlocksPanel();
    }

    function renderBlocksPanel() {
        const panel = document.getElementById('blocks-panel');
        if (!panel) return;
        const kpiCards = buildKpiCards(lastDashboard?.overview || {
            fieldsInScope: 0, totalAreaHa: 0, operationsTotal: 0, operationsCompleted: 0,
            operationsInProgress: 0, operationsPlanned: 0, overduePlanned: 0, avgActualYield: null
        });
        const kpisDisabled = !isBlockEnabled('kpis');
        panel.innerHTML = `
            <div class="reports-blocks-panel__section">
                <h4>Блоки отчёта</h4>
                <div class="reports-blocks-panel__list">
                    ${BLOCK_CATALOG.map(b => `
                        <label>
                            <input type="checkbox" data-block-toggle="${b.id}"${isBlockEnabled(b.id) ? ' checked' : ''}>
                            <span>${escapeHtml(b.title)}</span>
                        </label>`).join('')}
                </div>
            </div>
            <div class="reports-blocks-panel__section">
                <h4>Ключевые показатели</h4>
                <div class="reports-blocks-panel__list${kpisDisabled ? ' is-disabled' : ''}" id="kpi-toggles">
                    ${kpiCards.map(k => `
                        <label>
                            <input type="checkbox" data-kpi-toggle="${k.id}"${isKpiEnabled(k.id) ? ' checked' : ''}${kpisDisabled ? ' disabled' : ''}>
                            <span>${escapeHtml(k.label)}</span>
                        </label>`).join('')}
                </div>
            </div>`;
        panel.querySelectorAll('[data-block-toggle]').forEach(cb => {
            cb.addEventListener('change', () => {
                layoutState.blocks[cb.getAttribute('data-block-toggle')] = cb.checked;
                applyBlockVisibility();
                saveLayoutState();
                renderBlocksPanel();
                if (lastDashboard) renderKpis(lastDashboard.overview);
            });
        });
        panel.querySelectorAll('[data-kpi-toggle]').forEach(cb => {
            cb.addEventListener('change', () => {
                layoutState.kpis[cb.getAttribute('data-kpi-toggle')] = cb.checked;
                saveLayoutState();
                if (lastDashboard) renderKpis(lastDashboard.overview);
            });
        });
    }

    function resetLayout() {
        layoutState = defaultLayoutState();
        saveLayoutState();
        applyBlockOrder();
        applyBlockVisibility();
        renderBlocksPanel();
        if (lastDashboard) renderKpis(lastDashboard.overview);
    }

    function initLayoutStack() {
        if (layoutSortable || typeof Sortable === 'undefined') return;
        const stackEl = document.getElementById('reports-layout-grid');
        if (!stackEl) return;
        layoutState = layoutState || loadLayoutState();
        applyBlockOrder();
        applyBlockVisibility();
        layoutSortable = Sortable.create(stackEl, {
            animation: 180,
            handle: '.reports-block-drag',
            draggable: '.reports-layout-item',
            ghostClass: 'reports-layout-item--ghost',
            dragClass: 'reports-layout-item--drag',
            disabled: true,
            filter: '.reports-block--hidden',
            onEnd: () => {
                syncOrderFromDom();
                saveLayoutState();
            }
        });
    }

    function updatePrintHeader(data) {
        const meta = document.getElementById('report-print-meta');
        if (!meta) return;
        const org = summaryData?.organizationName || ('#' + (summaryData?.organizationId || ''));
        const scope = data.fieldIds?.length
            ? `Поля: ${data.fieldIds.length} из ${summaryData?.fields?.length || '?'}`
            : 'Все поля организации';
        const now = new Date().toLocaleString('ru-RU', {
            year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
        });
        meta.innerHTML = `
            <div><strong>${escapeHtml(org)}</strong></div>
            <div>Период: ${escapeHtml(data.dateFrom)} — ${escapeHtml(data.dateTo)}</div>
            <div>${escapeHtml(scope)}</div>
            <div class="reports-print-gen">Сформирован: ${escapeHtml(now)}</div>`;
    }

    async function exportPdf() {
        if (!lastDashboard) return;
        if (typeof html2canvas === 'undefined' || !window.jspdf) {
            showBanner('Библиотека PDF не загрузилась. Проверьте соединение и обновите страницу.', true);
            return;
        }
        const btn = document.getElementById('btn-export-pdf');
        const dash = document.getElementById('dashboard');
        btn.disabled = true;
        const prevText = btn.textContent;
        btn.textContent = 'Готовим PDF…';

        const EXPORT_WIDTH = 1080;
        // Скрытая копия дашборда — рендерим её, видимая страница не меняется.
        const holder = document.createElement('div');
        holder.className = 'geo-page-org geo-page-org--reports reports-exporting reports-export-holder';
        holder.style.cssText =
            'position:fixed;left:-10000px;top:0;width:' + EXPORT_WIDTH + 'px;background:#fff;';
        const clone = dash.cloneNode(true);
        clone.classList.remove('is-collapsed');
        clone.style.cssText = 'width:' + EXPORT_WIDTH + 'px;padding:20px;background:#fff;';
        clone.querySelector('#report-print-header')?.classList.add('is-visible');
        holder.appendChild(clone);
        document.body.appendChild(holder);

        try {
            await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)));

            const { jsPDF } = window.jspdf;
            const pdf = new jsPDF('p', 'mm', 'a4');
            const pageW = pdf.internal.pageSize.getWidth();
            const pageH = pdf.internal.pageSize.getHeight();
            const margin = 10;
            const contentW = pageW - margin * 2;
            const contentH = pageH - margin * 2;
            const gap = 4;
            const ROWS_PER_CHUNK = 32;   // строк таблицы на «кусок»
            let cursorY = margin;

            // Сжатый снимок элемента: меньший scale + JPEG → лёгкий файл.
            async function snap(el) {
                const canvas = await html2canvas(el, {
                    scale: 1.5,
                    backgroundColor: '#ffffff',
                    useCORS: true,
                    logging: false,
                    windowWidth: EXPORT_WIDTH
                });
                return { data: canvas.toDataURL('image/jpeg', 0.82), w: canvas.width, h: canvas.height };
            }

            function place(img) {
                const imgW = contentW;
                const imgH = img.h * imgW / img.w;
                if (imgH <= contentH) {
                    if (cursorY + imgH > margin + contentH + 0.1) {
                        pdf.addPage();
                        cursorY = margin;
                    }
                    pdf.addImage(img.data, 'JPEG', margin, cursorY, imgW, imgH);
                    cursorY += imgH + gap;
                } else {
                    if (cursorY > margin) { pdf.addPage(); cursorY = margin; }
                    let heightLeft = imgH, pos = margin;
                    pdf.addImage(img.data, 'JPEG', margin, pos, imgW, imgH);
                    heightLeft -= contentH;
                    while (heightLeft > 0) {
                        pdf.addPage();
                        pos = margin - (imgH - heightLeft);
                        pdf.addImage(img.data, 'JPEG', margin, pos, imgW, imgH);
                        heightLeft -= contentH;
                    }
                    const lastUsed = imgH - Math.floor(imgH / contentH) * contentH;
                    cursorY = margin + (lastUsed > 0 ? lastUsed : contentH) + gap;
                }
            }

            function buildTableChunk(blockEl, table, rows, withTitle) {
                const wrap = document.createElement('div');
                wrap.className = 'reports-block';
                wrap.style.cssText = 'padding:16px;background:#fff;width:' + EXPORT_WIDTH + 'px;box-shadow:none;border:1px solid #e2e8f0;border-radius:14px;';
                if (withTitle) {
                    const src = blockEl.querySelector('.reports-block-title');
                    if (src) {
                        const h = document.createElement('h3');
                        h.textContent = src.textContent;
                        h.style.cssText = 'font-size:15px;margin:0 0 12px;';
                        wrap.appendChild(h);
                    }
                }
                const tc = document.createElement('div');
                tc.className = 'table-container';
                const tbl = table.cloneNode(true);
                const tb = tbl.querySelector('tbody');
                tb.innerHTML = '';
                rows.forEach(r => tb.appendChild(r.cloneNode(true)));
                tc.appendChild(tbl);
                wrap.appendChild(tc);
                return wrap;
            }

            function buildFieldsRowTr(r) {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td><strong>${escapeHtml(r.fieldName)}</strong></td>
                    <td>${r.areaHa != null ? formatNum(r.areaHa, 1) : '—'}</td>
                    <td>${r.operationsCount}</td>
                    <td>${r.completedOperations}/${r.operationsCount}</td>
                    <td>${escapeHtml(r.latestCropName || '—')}</td>
                    <td>${r.latestCropYear != null ? r.latestCropYear : '—'}</td>
                    <td>${r.latestYield != null ? formatNum(r.latestYield, 2) : '—'}</td>`;
                return tr;
            }

            function buildOpsRowTr(r) {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td>${formatDate(r.operationAt)}</td>
                    <td>${escapeHtml(r.fieldName)}</td>
                    <td>${escapeHtml(r.name)}</td>
                    <td>${escapeHtml(r.categoryTitleRu)}</td>
                    <td>${escapeHtml(r.statusTitleRu)}</td>`;
                return tr;
            }

            async function renderTableBlock(itemEl) {
                const block = itemEl.querySelector('.reports-block');
                const table = itemEl.querySelector('table');
                if (!table) {
                    if (block) place(await snap(block));
                    return;
                }
                const blockId = itemEl.getAttribute('data-block-id');
                let dataRows = [];
                if (blockId === 'fields-table') dataRows = getFieldsRows();
                else if (blockId === 'ops-table') dataRows = getOpsRows();
                const allRows = dataRows.map(r =>
                    blockId === 'fields-table' ? buildFieldsRowTr(r) : buildOpsRowTr(r));
                if (!allRows.length) {
                    place(await snap(block));
                    return;
                }
                for (let i = 0; i < allRows.length; i += ROWS_PER_CHUNK) {
                    const chunk = allRows.slice(i, i + ROWS_PER_CHUNK);
                    const el = buildTableChunk(block, table, chunk, i === 0);
                    holder.appendChild(el);
                    // eslint-disable-next-line no-await-in-loop
                    await new Promise(r => requestAnimationFrame(r));
                    // eslint-disable-next-line no-await-in-loop
                    const img = await snap(el);
                    el.remove();
                    place(img);
                }
            }

            clone.querySelector('#layout-hint')?.remove();
            clone.querySelector('#blocks-panel')?.remove();
            clone.classList.remove('is-layout-edit');

            const stackItems = Array.from(clone.querySelectorAll('#reports-layout-grid .reports-layout-item'))
                .filter(el => !el.classList.contains('reports-block--hidden'));

            const header = clone.querySelector('#report-print-header');
            if (header && header.offsetHeight > 0) place(await snap(header));

            for (const itemEl of stackItems) {
                if (itemEl.querySelector('table')) {
                    // eslint-disable-next-line no-await-in-loop
                    await renderTableBlock(itemEl);
                } else {
                    const block = itemEl.querySelector('.reports-block');
                    if (block && block.offsetHeight > 0) place(await snap(block));
                }
            }

            const org = (summaryData?.organizationName || 'organization').replace(/[^\wа-яА-ЯёЁ\- ]/g, '').trim();
            pdf.save(`Отчёт ${org} ${lastDashboard.dateFrom}—${lastDashboard.dateTo}.pdf`);
        } catch (e) {
            console.error('PDF export:', e);
            showBanner('Не удалось сформировать PDF.', true);
        } finally {
            holder.remove();
            btn.disabled = false;
            btn.textContent = prevText;
        }
    }

    // ---------- Render dashboard ----------
    function renderDashboard(data) {
        lastDashboard = data;
        layoutState = layoutState || loadLayoutState();
        document.getElementById('dashboard').classList.remove('is-collapsed');
        const pdfBtn = document.getElementById('btn-export-pdf');
        const layoutBtn = document.getElementById('btn-layout-edit');
        if (pdfBtn) pdfBtn.disabled = false;
        if (layoutBtn) layoutBtn.disabled = false;
        initLayoutStack();
        applyBlockOrder();
        applyBlockVisibility();
        updatePrintHeader(data);
        renderKpis(data.overview);
        renderTimeline(data.operationsTimeline);
        renderDonut('chart-status', data.operationsByStatus, 'Нет операций по статусам');
        renderBarChart('chart-category', data.operationsByCategory, 'Нет операций по категориям');
        renderCropCharts(data.cropAreas);
        fillOpsTableFilters();
        renderFieldsTable();
        renderOpsTable();
        renderBlocksPanel();
        showBanner(
            `Отчёт за ${data.dateFrom} — ${data.dateTo}` +
            (data.fieldIds?.length ? ` (${data.fieldIds.length} пол.)` : ' (все поля)'),
            false
        );
    }

    async function buildReport() {
        const from = document.getElementById('filter-date-from').value;
        const to = document.getElementById('filter-date-to').value;
        if (!from || !to) {
            showBanner('Укажите период отчёта.', true);
            return;
        }
        const fieldIds = getSelectedFieldIds();
        const params = new URLSearchParams({ dateFrom: from, dateTo: to });
        if (fieldIds.length) params.set('fieldIds', fieldIds.join(','));

        const btn = document.getElementById('btn-build-report');
        btn.disabled = true;
        btn.textContent = 'Формирование…';
        showBanner('', false);
        try {
            const res = await fetch(`${API}/dashboard?${params}`, fetchOpts);
            if (!res.ok) {
                showBanner('Не удалось сформировать отчёт (HTTP ' + res.status + ').', true);
                return;
            }
            renderDashboard(await res.json());
        } catch (_e) {
            showBanner('Ошибка сети при загрузке отчёта.', true);
        } finally {
            btn.disabled = false;
            btn.textContent = 'Сформировать отчёт';
        }
    }

    function bindSortHeaders(tableId, sortState, rerender) {
        document.querySelectorAll(`#${tableId} thead th[data-sort]`).forEach(th => {
            th.classList.add('reports-th-sortable');
            th.addEventListener('click', () => {
                const key = th.getAttribute('data-sort');
                if (sortState.key === key) sortState.dir *= -1;
                else { sortState.key = key; sortState.dir = 1; }
                rerender();
            });
        });
    }

    function init() {
        setDefaultPeriod(12);
        document.getElementById('btn-build-report').addEventListener('click', buildReport);
        document.getElementById('btn-fields-all').addEventListener('click', () => {
            document.querySelectorAll('#fields-checklist input').forEach(cb => { cb.checked = true; });
            updateScopeHint();
        });
        document.getElementById('btn-fields-none').addEventListener('click', () => {
            document.querySelectorAll('#fields-checklist input').forEach(cb => { cb.checked = false; });
            updateScopeHint();
        });
        document.getElementById('fields-search')?.addEventListener('input', (e) => {
            renderFieldsChecklist(e.target.value);
        });
        document.querySelectorAll('.reports-preset-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                setDefaultPeriod(parseInt(btn.getAttribute('data-months'), 10) || 0);
                document.querySelectorAll('.reports-preset-btn').forEach(b => b.classList.remove('is-active'));
                btn.classList.add('is-active');
            });
        });

        document.getElementById('fields-table-search')?.addEventListener('input', renderFieldsTable);
        document.getElementById('ops-table-search')?.addEventListener('input', renderOpsTable);
        document.getElementById('ops-table-status')?.addEventListener('change', renderOpsTable);
        document.getElementById('ops-table-category')?.addEventListener('change', renderOpsTable);
        document.getElementById('btn-export-fields')?.addEventListener('click', exportFields);
        document.getElementById('btn-export-ops')?.addEventListener('click', exportOps);
        document.getElementById('btn-export-pdf')?.addEventListener('click', exportPdf);
        document.getElementById('btn-layout-edit')?.addEventListener('click', () => {
            if (!lastDashboard) return;
            setLayoutEditMode(!layoutEditMode);
        });
        document.getElementById('btn-layout-done')?.addEventListener('click', () => setLayoutEditMode(false));
        document.getElementById('btn-layout-reset')?.addEventListener('click', resetLayout);

        layoutState = loadLayoutState();

        bindSortHeaders('fields-table', fieldsSort, renderFieldsTable);
        bindSortHeaders('recent-ops-table', opsSort, renderOpsTable);

        loadSummary().then(() => {
            const params = new URLSearchParams(window.location.search);
            const fieldId = params.get('fieldId');
            if (fieldId) {
                document.querySelectorAll('#fields-checklist input').forEach(cb => {
                    cb.checked = cb.value === fieldId;
                });
                updateScopeHint();
            }
            buildReport();
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
