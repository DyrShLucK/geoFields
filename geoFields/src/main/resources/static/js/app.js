// =====================================================
// ПАРАМЕТРЫ API И БАЗОВЫЕ НАСТРОЙКИ
// =====================================================
const API_URL = "http://localhost:8080";
const PYTHON_API_URL = "http://localhost:8001";
const _apiBase = String(API_URL || "").trim().replace(/\/$/, "");
const _pythonApiBase = String(PYTHON_API_URL || "").trim().replace(/\/$/, "");

const fetchOpts = {
    credentials: _apiBase ? "include" : "same-origin"
};
const pythonFetchOpts = fetchOpts; // через Java нужна сессия и CSRF

function apiPath(path) {
    const p = path.startsWith("/") ? path : "/" + path;
    return _apiBase ? _apiBase + p : p;
}

// =====================================================
// PYTHON API PATH (СОВМЕСТИМОСТЬ)
// =====================================================
function pythonApiPath(path) {
    // Прямой путь к Python-сервису (как было раньше).
    const p = path.startsWith("/") ? path : "/" + path;
    return _pythonApiBase ? _pythonApiBase + p : p;
}

// =====================================================
// ГЛОБАЛЬНЫЕ ПЕРЕМЕННЫЕ И НАСТРОЙКИ КАРТЫ
// =====================================================
const SARATOV_CENTER = [46.03, 51.54]; 
const SARATOV_ZOOM = 7.5;

let map = null;
let loadedFieldsGeoJson = null;
let currentNdviLayerId = null;

// Переменные измерительной линейки
let isMeasuring = false;
let measurePoints = [];
let measureGeoJson = {
    type: "FeatureCollection",
    features: []
};

let activeNdviLayerIds = [];
let activeElevationLayerIds = [];
let activeSlopeLayerIds = [];
/** @type {Map<string, {min: number, max: number}>} */
const elevationRangeByFieldId = new Map();
let highlightedFieldId = null;
let activeFieldPopup = null;
let activeFieldPopupFieldId = null;
let suppressPopupCloseHighlightReset = false;
const ndviSelectedFieldIds = new Set();
let fieldContextMenuTargetId = null;

const ANALYTICS_PANEL_WIDTH_KEY = "geofields-analytics-panel-width";
const ANALYTICS_PANEL_MIN_WIDTH = 320;
const ANALYTICS_PANEL_MAX_WIDTH_RATIO = 0.75;
const FIELD_HISTORY_PANEL_HEIGHT_KEY = "geofields-field-history-panel-height";
const FIELD_HISTORY_PANEL_MIN_HEIGHT = 180;
const FIELD_HISTORY_PANEL_MAX_HEIGHT_RATIO = 0.65;

// Шаблоны ролевой модели
const MENU_TEMPLATES = {
    "USER": [
        { label: "Карта полей", path: "/", icon: "🗺️", active: true }
    ],
    "AGRONOMIST": [
        { label: "Карта полей", path: "/", icon: "🗺️", active: true },
        { label: "История посевов", path: "/org/agronomist", icon: "🌾" }
    ],
    "ORG_MANAGER": [
        { label: "Карта полей", path: "/", icon: "🗺️", active: true },
        { label: "Заявки и инвайты", path: "/org/manager", icon: "📋" }
    ],
    "ORG_ADMIN": [
        { label: "Карта полей", path: "/", icon: "🗺️", active: true },
        { label: "История посевов", path: "/org/agronomist", icon: "🌾" },
        { label: "Заявки и инвайты", path: "/org/manager", icon: "📋" },
        { label: "Управление организацией", path: "/org/admin", icon: "⚙️" }
    ]
};

// =====================================================
// ИНИЦИАЛИЗАЦИЯ ПОСЛЕ ЗАГРУЗКИ DOM (Отказоустойчивость)
// =====================================================
document.addEventListener("DOMContentLoaded", async () => {
    // 1. Изолированная инициализация профиля и меню
    try {
        await checkSessionAndBuildMenu();
    } catch (e) {
        console.warn("Ошибка построения сессии. Запуск гостевого режима интерфейса:", e);
        fallbackProfileUI();
    }

    // 2. Изолированная инициализация элементов интерфейса
    try { setupLayerControls(); } catch (e) { console.error("Сбой LayerControls:", e); }
    try { setupFilterControls(); } catch (e) { console.error("Сбой FilterControls:", e); }
    try { setupImportModal(); } catch (e) { console.error("Сбой ImportModal:", e); }
    try { setupAnalyticsTabs(); } catch (e) { console.error("Сбой AnalyticsTabs:", e); }
    try { ensureWeatherTabFallbackContent(); } catch (e) { console.error("Сбой WeatherFallback:", e); }
    try { setupSidebarAndPanels(); } catch (e) { console.error("Сбой SidebarAndPanels:", e); }
    try { ensureNdviLegend(); } catch (e) { console.error("Сбой NdviLegend:", e); }
    try { ensureElevationLegend(); } catch (e) { console.error("Сбой ElevationLegend:", e); }
    try { ensureFieldContextMenu(); } catch (e) { console.error("Сбой FieldContextMenu:", e); }
    try { setupSlopeAnalyticsCard(); } catch (e) { console.error("Сбой SlopeAnalytics:", e); }
    try { setupAnalyticsCardCollapsing(); } catch (e) { console.error("Сбой AnalyticsCollapsing:", e); }
    try { setupNdviCalculation(); } catch (e) { console.error("Сбой NdviCalculation:", e); }
    try { setupSlopeCalculation(); } catch (e) { console.error("Сбой SlopeCalculation:", e); }
    try { initChartTooltips(); } catch (e) { console.error("Сбой ChartTooltips:", e); }

    // 3. Безопасная инициализация карты MapLibre GL
    if (typeof maplibregl !== "undefined") {
        try {
            initMap();
        } catch (e) {
            console.error("Ошибка при инициализации MapLibre:", e);
            updateSyncStatus(false);
        }
    } else {
        console.warn("Библиотека MapLibre GL недоступна.");
        updateSyncStatus(false);
    }
});

// =====================================================
// БЕЗОПАСНАЯ ИНИЦИАЛИЗАЦИЯ КАРТЫ
// =====================================================
function initMap() {
    // Меньше одновременных raster-запросов => меньше фризов при pan/zoom.
    if (typeof maplibregl?.setMaxParallelImageRequests === "function") {
        maplibregl.setMaxParallelImageRequests(8);
    }

    map = new maplibregl.Map({
        container: "map",
        style: {
            version: 8,
            sources: {
                "google-satellite": {
                    type: "raster",
                    tiles: ["https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}"],
                    tileSize: 256
                },
                "osm": {
                    type: "raster",
                    tiles: ["https://a.tile.openstreetmap.org/{z}/{x}/{y}.png"],
                    tileSize: 256,
                    attribution: "© OpenStreetMap"
                }
            },
            layers: [
                { id: "osm", type: "raster", source: "osm", layout: { visibility: "none" } },
                { id: "google-satellite", type: "raster", source: "google-satellite", layout: { visibility: "visible" } }
            ]
        },
        center: SARATOV_CENTER,
        zoom: SARATOV_ZOOM
    });

    map.addControl(new maplibregl.NavigationControl(), "top-right");

    map.on("load", async () => {
        await loadFields();
        setupMeasureTool();
        populateNdviFieldsChecklist(loadedFieldsGeoJson);
    });
}

// =====================================================
// АВТОМАТИЧЕСКИЙ РЕЗЕРВНЫЙ ГОСТЕВОЙ ИНТЕРФЕЙС
// =====================================================
function fallbackProfileUI() {
    const nameEl = document.getElementById("user-display-name");
    const charEl = document.getElementById("user-avatar-char");
    const roleEl = document.getElementById("user-display-role");

    if (nameEl) nameEl.textContent = "Гость";
    if (charEl) charEl.textContent = "Г";
    if (roleEl) roleEl.textContent = "Демонстрационный доступ";

    buildMenuForRole("USER");
    updateSyncStatus(false);
}

// =====================================================
// СТАТУС СИНХРОНИЗАЦИИ И СЕССИЯ
// =====================================================
async function checkSessionAndBuildMenu() {
    const response = await fetch(apiPath("/api/session/context"), fetchOpts);
    if (!response.ok) throw new Error();

    const data = await response.json();
    const fullName = data.currentUserFullName || data.currentLogin || "Пользователь";
    
    let rawRole = data.currentUserRole || data.role || "USER";
    if (typeof rawRole === 'string' && rawRole.startsWith("ROLE_")) {
        rawRole = rawRole.substring(5);
    }
    
    const nameEl = document.getElementById("user-display-name");
    const charEl = document.getElementById("user-avatar-char");
    const roleEl = document.getElementById("user-display-role");

    if (nameEl) nameEl.textContent = fullName;
    if (charEl) charEl.textContent = fullName.charAt(0).toUpperCase();

    const roleLabels = {
        "USER": "Пользователь",
        "AGRONOMIST": "Агроном",
        "ORG_MANAGER": "Менеджер",
        "ORG_ADMIN": "Администратор"
    };
    if (roleEl) roleEl.textContent = roleLabels[rawRole] || rawRole;

    buildMenuForRole(rawRole);
    updateSyncStatus(true);
}

// =====================================================
// ПОСТРОЕНИЕ МЕНЮ ПО РОЛИ
// =====================================================
function buildMenuForRole(role) {
    const menuContainer = document.getElementById("dynamic-menu");
    if (!menuContainer) return;

    const items = MENU_TEMPLATES[role] || MENU_TEMPLATES["USER"];
    
    let html = '<div class="sidebar-heading">Модули systems</div>';
    items.forEach(item => {
        const activeClass = item.active ? "active" : "";
        html += `
            <a href="${item.path}" class="sidebar-item ${activeClass}">
                <span class="sidebar-icon">${item.icon}</span> ${item.label}
            </a>
        `;
    });

    html += `
        <div class="sidebar-heading" style="margin-top:20px;">Операции</div>
        <button class="sidebar-item" id="btn-import-trigger" style="border:none; background:transparent; width:100%; text-align:left; cursor:pointer;">
            <span class="sidebar-icon">📥</span> Импорт SHP / GeoJSON
        </button>
    `;

    menuContainer.innerHTML = html;

    document.getElementById("btn-import-trigger")?.addEventListener("click", () => {
        document.getElementById("import-modal")?.classList.add("open");
    });
}

// =====================================================
// ОБНОВЛЕНИЕ СТАТУСА СИНХРОНИЗАЦИИ
// =====================================================
function updateSyncStatus(isOnline) {
    const statusEl = document.getElementById("connection-status");
    if (!statusEl) return;

    if (isOnline) {
        statusEl.innerHTML = `<span class="status-dot"></span> Данные синхронизированы`;
        statusEl.className = "sync-status";
    } else {
        statusEl.innerHTML = `<span class="status-dot"></span> Ошибка синхронизации. Повторить?`;
        statusEl.className = "sync-status err";
    }
}

document.getElementById("connection-status")?.addEventListener("click", async () => {
    const statusEl = document.getElementById("connection-status");
    if (statusEl?.classList.contains("err")) {
        statusEl.textContent = "⌛ Синхронизация...";
        try {
            await loadFields();
            await checkSessionAndBuildMenu();
        } catch(e) {
            fallbackProfileUI();
        }
    }
});

// =====================================================
// ПОЛЯ: ВЫДЕЛЕНИЕ, NDVI, КОНТЕКСТНОЕ МЕНЮ
// =====================================================
function parseFieldHistory(raw) {
    if (!raw) return [];
    if (Array.isArray(raw)) return raw;
    if (typeof raw === "string") {
        try {
            const parsed = JSON.parse(raw);
            return Array.isArray(parsed) ? parsed : [];
        } catch {
            return [];
        }
    }
    return [];
}

function escapeHtml(value) {
    if (value == null) return "";
    return String(value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

function getFeatureFieldId(feature) {
    const props = feature?.properties || {};
    return String(props.id ?? props.fieldId ?? feature?.id ?? "");
}

function getFieldBoundsById(fieldId) {
    if (!loadedFieldsGeoJson?.features?.length) return null;
    const targetId = String(fieldId);
    const feature = loadedFieldsGeoJson.features.find(f => getFeatureFieldId(f) === targetId);
    if (!feature?.geometry) return null;

    let minLng = Infinity;
    let minLat = Infinity;
    let maxLng = -Infinity;
    let maxLat = -Infinity;

    const scan = (coords) => {
        if (!Array.isArray(coords)) return;
        if (typeof coords[0] === "number" && typeof coords[1] === "number") {
            const lng = Number(coords[0]);
            const lat = Number(coords[1]);
            if (!Number.isFinite(lng) || !Number.isFinite(lat)) return;
            minLng = Math.min(minLng, lng);
            minLat = Math.min(minLat, lat);
            maxLng = Math.max(maxLng, lng);
            maxLat = Math.max(maxLat, lat);
            return;
        }
        coords.forEach(scan);
    };
    scan(feature.geometry.coordinates);

    if (![minLng, minLat, maxLng, maxLat].every(Number.isFinite)) return null;
    return [minLng, minLat, maxLng, maxLat];
}

function toMapFeatureId(fieldId) {
    const n = Number(fieldId);
    return Number.isFinite(n) ? n : fieldId;
}

function setFieldFeatureState(fieldId, statePatch) {
    if (!map || fieldId == null || fieldId === "") return;
    try {
        map.setFeatureState(
            { source: "fields-source", id: toMapFeatureId(fieldId) },
            statePatch
        );
    } catch (e) {
        console.warn("setFeatureState:", fieldId, e);
    }
}

function applyFieldHighlight(fieldId) {
    if (highlightedFieldId != null) {
        setFieldFeatureState(highlightedFieldId, { highlight: false });
    }
    highlightedFieldId = fieldId != null ? String(fieldId) : null;
    if (highlightedFieldId != null) {
        setFieldFeatureState(highlightedFieldId, { highlight: true });
    }
}

function syncNdviCheckboxUI(fieldId, selected) {
    const cb = document.querySelector(`.ndvi-filter-checkbox[value="${fieldId}"]`);
    if (cb) cb.checked = selected;
}

function selectAllNdviFields(selected) {
    document.querySelectorAll(".ndvi-filter-checkbox").forEach(cb => {
        setNdviFieldSelected(cb.value, selected);
    });
}

function selectAllCheckboxes(cssClass, selected) {
    document.querySelectorAll(`.${cssClass}`).forEach(cb => {
        cb.checked = selected;
    });
}

function selectAllCropFields(selected) {
    selectAllCheckboxes("crop-filter-checkbox", selected);
    updateCropDistributionChart();
}

function selectAllSoilFields(selected) {
    selectAllCheckboxes("soil-filter-checkbox", selected);
    document.getElementById("soil-fields-checklist")?.dispatchEvent(new Event("change", { bubbles: true }));
}

function ensureFieldsToolbar(toolbarId, containerId, onSelectAll, onSelectNone) {
    const box = document.getElementById(containerId);
    if (!box || document.getElementById(toolbarId)) return;

    const toolbar = document.createElement("div");
    toolbar.id = toolbarId;
    toolbar.className = "ndvi-fields-toolbar";
    toolbar.innerHTML = `
        <button type="button" class="ndvi-list-btn" data-action="all">Все поля</button>
        <button type="button" class="ndvi-list-btn" data-action="none">Снять все</button>
    `;
    box.parentElement?.insertBefore(toolbar, box);
    toolbar.querySelector("[data-action='all']")?.addEventListener("click", onSelectAll);
    toolbar.querySelector("[data-action='none']")?.addEventListener("click", onSelectNone);
}

function ensureNdviFieldsToolbar() {
    ensureFieldsToolbar(
        "ndvi-fields-toolbar",
        "ndvi-fields-checklist",
        () => selectAllNdviFields(true),
        () => selectAllNdviFields(false)
    );
}

function ensureCropFieldsToolbar() {
    ensureFieldsToolbar(
        "crop-fields-toolbar",
        "crop-fields-checklist",
        () => selectAllCropFields(true),
        () => selectAllCropFields(false)
    );
}

function ensureSoilFieldsToolbar() {
    ensureFieldsToolbar(
        "soil-fields-toolbar",
        "soil-fields-checklist",
        () => selectAllSoilFields(true),
        () => selectAllSoilFields(false)
    );
}

function toggleAnalyticsCheckbox(cssClass, fieldId) {
    const cb = document.querySelector(`.${cssClass}[value="${CSS.escape(String(fieldId))}"]`);
    if (!cb) return;
    cb.checked = !cb.checked;
    cb.dispatchEvent(new Event("change", { bubbles: true }));
}

function setNdviFieldSelected(fieldId, selected) {
    const idStr = String(fieldId);
    if (selected) {
        ndviSelectedFieldIds.add(idStr);
    } else {
        ndviSelectedFieldIds.delete(idStr);
    }
    syncNdviCheckboxUI(idStr, selected);
    setFieldFeatureState(idStr, { ndviSelected: selected });
    updateFieldContextMenuLabels();
}

function toggleNdviFieldSelected(fieldId) {
    setNdviFieldSelected(fieldId, !ndviSelectedFieldIds.has(String(fieldId)));
}

function hideFieldContextMenu() {
    document.getElementById("field-context-menu")?.classList.add("hidden");
    fieldContextMenuTargetId = null;
}

function updateFieldContextMenuLabels() {
    if (fieldContextMenuTargetId == null) return;
    const menu = document.getElementById("field-context-menu");
    if (!menu) return;

    const id = String(fieldContextMenuTargetId);
    const ndviBtn = menu.querySelector("[data-action='toggle-ndvi']");
    const cropBtn = menu.querySelector("[data-action='toggle-crop']");
    const soilBtn = menu.querySelector("[data-action='toggle-soil']");

    if (ndviBtn) {
        const ndviSelected = ndviSelectedFieldIds.has(id);
        ndviBtn.textContent = ndviSelected ? "Убрать из расчёта NDVI" : "Выбрать для NDVI";
    }
    if (cropBtn) {
        const cropCb = document.querySelector(`.crop-filter-checkbox[value="${CSS.escape(id)}"]`);
        cropBtn.textContent = cropCb?.checked
            ? "Убрать из статистики культур"
            : "Выбрать для статистики культур";
    }
    if (soilBtn) {
        const soilCb = document.querySelector(`.soil-filter-checkbox[value="${CSS.escape(id)}"]`);
        soilBtn.textContent = soilCb?.checked
            ? "Убрать из статистики почвы"
            : "Выбрать для статистики почвы";
    }
}

function syncFieldHistoryPanelLayout() {
    const panel = document.getElementById("field-history-panel");
    const analytics = document.getElementById("analytics-panel");
    if (!panel) return;
    const rightInset = analytics?.classList.contains("open")
        ? Math.round(analytics.getBoundingClientRect().width)
        : 0;
    panel.style.right = `${rightInset}px`;
}

function renderFieldHistoryPanel(fieldId, fieldName) {
    const body = document.getElementById("field-history-body");
    const title = document.getElementById("field-history-title");
    if (!body || !title) return;
    const idStr = String(fieldId);
    const feature = loadedFieldsGeoJson?.features?.find(f => getFeatureFieldId(f) === idStr);
    const history = parseFieldHistory(feature?.properties?.history);
    title.textContent = `История поля: ${fieldName || `#${idStr}`}`;
    if (!history.length) {
        body.innerHTML = `<p class="muted font-sm m-0">История севооборота отсутствует.</p>`;
        return;
    }
    const sorted = [...history].sort((a, b) => (b.cropYear || 0) - (a.cropYear || 0));
    body.innerHTML = `
        <table class="field-history-table">
            <thead>
                <tr>
                    <th>Год</th>
                    <th>Культура</th>
                    <th>Посев</th>
                    <th>Уборка</th>
                    <th>Площадь, га</th>
                    <th>Урожайность</th>
                </tr>
            </thead>
            <tbody>
                ${sorted.map(r => `
                    <tr>
                        <td>${r.cropYear || "—"}</td>
                        <td>${escapeHtml(r.cropName || "—")}</td>
                        <td>${r.sowingDate || "—"}</td>
                        <td>${r.harvestDate || "—"}</td>
                        <td>${r.sownAreaHa || "—"}</td>
                        <td>${r.actualYield || "—"}</td>
                    </tr>
                `).join("")}
            </tbody>
        </table>
    `;
}

function openFieldHistoryPanel(fieldId, fieldName) {
    const panel = ensureFieldHistoryPanel();
    if (!panel) return;
    renderFieldHistoryPanel(fieldId, fieldName);
    syncFieldHistoryPanelLayout();
    panel.classList.add("open");
}

function closeFieldHistoryPanel() {
    document.getElementById("field-history-panel")?.classList.remove("open");
}

function ensureFieldHistoryPanel() {
    let panel = document.getElementById("field-history-panel");
    if (panel) return panel;
    panel = document.createElement("section");
    panel.id = "field-history-panel";
    panel.className = "field-history-panel";
    panel.innerHTML = `
        <div class="field-history-resize-handle" id="field-history-resize-handle" title="Изменить высоту"></div>
        <div class="field-history-header">
            <div class="field-history-title" id="field-history-title">История поля</div>
            <button type="button" class="field-history-close" id="field-history-close" aria-label="Закрыть">×</button>
        </div>
        <div class="field-history-body" id="field-history-body"></div>
    `;
    document.body.appendChild(panel);
    document.getElementById("field-history-close")?.addEventListener("click", closeFieldHistoryPanel);

    const handle = document.getElementById("field-history-resize-handle");
    if (!handle) return panel;
    const saved = Number(localStorage.getItem(FIELD_HISTORY_PANEL_HEIGHT_KEY));
    if (saved >= FIELD_HISTORY_PANEL_MIN_HEIGHT) {
        panel.style.setProperty("--field-history-panel-height", `${saved}px`);
    }
    const maxHeight = () => Math.round(window.innerHeight * FIELD_HISTORY_PANEL_MAX_HEIGHT_RATIO);
    const clampHeight = (h) => Math.max(FIELD_HISTORY_PANEL_MIN_HEIGHT, Math.min(maxHeight(), Math.round(h)));
    const applyHeight = (h) => {
        const next = clampHeight(h);
        panel.style.setProperty("--field-history-panel-height", `${next}px`);
        return next;
    };
    let dragging = false;
    let activePointerId = null;
    const stopDrag = (e) => {
        if (!dragging) return;
        if (e?.pointerId != null && activePointerId != null && e.pointerId !== activePointerId) return;
        dragging = false;
        activePointerId = null;
        handle.classList.remove("is-dragging");
        panel.classList.remove("is-resizing");
        document.body.style.cursor = "";
        document.body.style.userSelect = "";
        try {
            if (e?.pointerId != null) handle.releasePointerCapture(e.pointerId);
        } catch (_) { /* ignore */ }
        const current = parseInt(getComputedStyle(panel).getPropertyValue("--field-history-panel-height"), 10);
        if (Number.isFinite(current)) {
            localStorage.setItem(FIELD_HISTORY_PANEL_HEIGHT_KEY, String(current));
        }
    };
    const onPointerMove = (e) => {
        if (!dragging) return;
        e.preventDefault();
        const next = window.innerHeight - e.clientY;
        applyHeight(next);
    };
    const startDrag = (e) => {
        if (!panel.classList.contains("open")) return;
        if (e.button !== undefined && e.button !== 0) return;
        e.preventDefault();
        dragging = true;
        activePointerId = e.pointerId;
        handle.classList.add("is-dragging");
        panel.classList.add("is-resizing");
        document.body.style.cursor = "ns-resize";
        document.body.style.userSelect = "none";
        try {
            handle.setPointerCapture(e.pointerId);
        } catch (_) { /* ignore */ }
    };
    handle.addEventListener("pointerdown", startDrag);
    handle.addEventListener("pointermove", onPointerMove);
    handle.addEventListener("pointerup", stopDrag);
    handle.addEventListener("pointercancel", stopDrag);
    document.addEventListener("pointermove", onPointerMove);
    document.addEventListener("pointerup", stopDrag);
    window.addEventListener("resize", () => syncFieldHistoryPanelLayout());
    return panel;
}

function showFieldContextMenu(screenX, screenY, fieldId, fieldName) {
    const menu = document.getElementById("field-context-menu");
    if (!menu) return;
    fieldContextMenuTargetId = String(fieldId);
    updateFieldContextMenuLabels();
    const title = menu.querySelector("[data-field-title]");
    if (title) title.textContent = fieldName || `Поле #${fieldId}`;
    menu.classList.remove("hidden");
    const pad = 8;
    const rect = menu.getBoundingClientRect();
    let left = screenX;
    let top = screenY;
    if (left + rect.width > window.innerWidth - pad) left = window.innerWidth - rect.width - pad;
    if (top + rect.height > window.innerHeight - pad) top = window.innerHeight - rect.height - pad;
    menu.style.left = `${left}px`;
    menu.style.top = `${top}px`;
}

function ensureFieldContextMenu() {
    const existing = document.getElementById("field-context-menu");
    if (existing && !existing.querySelector("[data-action='toggle-crop']")) {
        existing.remove();
    }
    if (document.getElementById("field-context-menu")) return;
    const menu = document.createElement("div");
    menu.id = "field-context-menu";
    menu.className = "field-context-menu hidden";
    menu.innerHTML = `
        <div data-field-title style="padding:6px 12px 4px;font-size:12px;font-weight:600;color:#64748b;"></div>
        <button type="button" data-action="show-history">Показать историю</button>
        <button type="button" data-action="toggle-ndvi">Выбрать для NDVI</button>
        <button type="button" data-action="toggle-crop">Выбрать для статистики культур</button>
        <button type="button" data-action="toggle-soil">Выбрать для статистики почвы</button>
    `;
    document.body.appendChild(menu);

    menu.querySelector("[data-action='show-history']")?.addEventListener("click", () => {
        if (fieldContextMenuTargetId != null) {
            const feature = loadedFieldsGeoJson?.features?.find(f => getFeatureFieldId(f) === String(fieldContextMenuTargetId));
            openFieldHistoryPanel(fieldContextMenuTargetId, feature?.properties?.name);
        }
        hideFieldContextMenu();
    });
    menu.querySelector("[data-action='toggle-ndvi']")?.addEventListener("click", () => {
        if (fieldContextMenuTargetId != null) toggleNdviFieldSelected(fieldContextMenuTargetId);
        hideFieldContextMenu();
    });
    menu.querySelector("[data-action='toggle-crop']")?.addEventListener("click", () => {
        if (fieldContextMenuTargetId != null) toggleAnalyticsCheckbox("crop-filter-checkbox", fieldContextMenuTargetId);
        hideFieldContextMenu();
    });
    menu.querySelector("[data-action='toggle-soil']")?.addEventListener("click", () => {
        if (fieldContextMenuTargetId != null) toggleAnalyticsCheckbox("soil-filter-checkbox", fieldContextMenuTargetId);
        hideFieldContextMenu();
    });

    document.addEventListener("click", (e) => {
        if (!menu.contains(e.target)) hideFieldContextMenu();
    });
    document.addEventListener("keydown", (e) => {
        if (e.key === "Escape") hideFieldContextMenu();
    });
}

function onFieldLayerClick(e) {
    if (isMeasuring || !e.features?.length) return;
    const feature = e.features[0];
    const fieldId = getFeatureFieldId(feature);
    const properties = feature.properties || {};
    if (activeFieldPopup) {
        // При переключении между полями не сбрасываем highlight через close-обработчик старого popup.
        suppressPopupCloseHighlightReset = true;
        activeFieldPopup.remove();
        activeFieldPopup = null;
        activeFieldPopupFieldId = null;
        suppressPopupCloseHighlightReset = false;
    }
    applyFieldHighlight(fieldId);

    const name = properties.name || "Без названия";
    const area = properties.area ? Number(properties.area).toFixed(1) : "Н/Д";
    const historyRecords = parseFieldHistory(properties.history);

    let latestRecordHtml = "";
    if (historyRecords.length > 0) {
        historyRecords.sort((a, b) => (b.cropYear || 0) - (a.cropYear || 0));
        const latest = historyRecords[0];
        const crop = latest.cropName || "Неизвестно";
        const year = latest.cropYear || "—";
        const sowing = latest.sowingDate || "—";
        const yieldVal = latest.actualYield ? `${latest.actualYield} ц/га` : "—";
        latestRecordHtml = `
            <div style="margin-top: 8px; padding-top: 8px; border-top: 1px solid #e2e8f0; font-size: 11px; line-height: 1.4;">
                <strong style="display:block; color: #0f172a; margin-bottom: 2px;">Последний сев (${year} г.):</strong>
                <span style="display:block; color: #475569;">🌾 Культура: <strong style="color: #0f172a;">${crop}</strong></span>
                <span style="display:block; color: #475569;">📅 Дата посева: ${sowing}</span>
                <span style="display:block; color: #475569;">🚜 Урожайность: ${yieldVal}</span>
            </div>
        `;
    } else {
        latestRecordHtml = `
            <div style="margin-top: 8px; padding-top: 8px; border-top: 1px solid #e2e8f0; font-size: 11px; color: #94a3b8; text-align: center;">
                История севооборота отсутствует
            </div>
        `;
    }

    // setDOMContent + обработчик до addTo: setHTML не вставляет узлы в document до addTo(map).
    const popupRoot = document.createElement("div");
    popupRoot.style.cssText = "font-family:'Inter', sans-serif; padding:4px; min-width:180px;";
    popupRoot.innerHTML = `
        <strong style="display:block; margin-bottom:4px; color:#10b981; font-size:13px;">🌿 ${escapeHtml(name)}</strong>
        <span style="font-size:12px; color:#64748b; font-weight:500;">Площадь: ${area} га</span>
        ${latestRecordHtml}
    `;

    const elevationBtn = document.createElement("button");
    elevationBtn.type = "button";
    elevationBtn.className = "field-popup-elevation-btn";
    elevationBtn.textContent = "🏔️ Показать рельеф (SRTM)";
    elevationBtn.style.cssText =
        "margin-top:10px;width:100%;padding:6px 10px;border:1px solid #cbd5e1;border-radius:6px;background:#f8fafc;cursor:pointer;font-size:12px;font-weight:600;color:#0f172a;";
    elevationBtn.addEventListener("click", (ev) => {
        ev.preventDefault();
        ev.stopPropagation();
        showFieldElevation(fieldId, elevationBtn);
    });
    popupRoot.appendChild(elevationBtn);

    const slopeBtn = document.createElement("button");
    slopeBtn.type = "button";
    slopeBtn.className = "field-popup-slope-btn";
    slopeBtn.textContent = "📐 Показать уклон";
    slopeBtn.style.cssText =
        "margin-top:6px;width:100%;padding:6px 10px;border:1px solid #cbd5e1;border-radius:6px;background:#f8fafc;cursor:pointer;font-size:12px;font-weight:600;color:#0f172a;";
    slopeBtn.addEventListener("click", (ev) => {
        ev.preventDefault();
        ev.stopPropagation();
        showFieldSlope(fieldId, slopeBtn);
    });
    popupRoot.appendChild(slopeBtn);

    activeFieldPopup = new maplibregl.Popup({ closeOnClick: false })
        .setLngLat(e.lngLat)
        .setDOMContent(popupRoot);

    activeFieldPopup.on("close", () => {
        const closingFieldId = activeFieldPopupFieldId != null ? String(activeFieldPopupFieldId) : null;
        if (!suppressPopupCloseHighlightReset && closingFieldId != null && highlightedFieldId === closingFieldId) {
            applyFieldHighlight(null);
        }
        activeFieldPopup = null;
        activeFieldPopupFieldId = null;
    });

    activeFieldPopup.addTo(map);
    activeFieldPopupFieldId = fieldId;
}

function onMapClickClearFieldSelection(e) {
    if (isMeasuring || !map) return;
    const hit = map.queryRenderedFeatures(e.point, { layers: ["fields-fill"] });
    if (hit.length > 0) return;
    if (activeFieldPopup) {
        suppressPopupCloseHighlightReset = true;
        activeFieldPopup.remove();
        activeFieldPopup = null;
        activeFieldPopupFieldId = null;
        suppressPopupCloseHighlightReset = false;
    }
    applyFieldHighlight(null);
    hideFieldContextMenu();
}

function onFieldLayerContextMenu(e) {
    if (isMeasuring || !e.features?.length) return;
    e.preventDefault();
    if (activeFieldPopup) {
        suppressPopupCloseHighlightReset = true;
        activeFieldPopup.remove();
        activeFieldPopup = null;
        activeFieldPopupFieldId = null;
        suppressPopupCloseHighlightReset = false;
    }
    const feature = e.features[0];
    const fieldId = getFeatureFieldId(feature);
    applyFieldHighlight(fieldId);
    const name = feature.properties?.name || `Поле #${fieldId}`;
    showFieldContextMenu(e.originalEvent.clientX, e.originalEvent.clientY, fieldId, name);
}

function bindFieldLayerHandlers() {
    if (!map) return;
    map.off("click", "fields-fill", onFieldLayerClick);
    map.off("contextmenu", "fields-fill", onFieldLayerContextMenu);
    map.off("click", onMapClickClearFieldSelection);
    map.on("click", "fields-fill", onFieldLayerClick);
    map.on("contextmenu", "fields-fill", onFieldLayerContextMenu);
    map.on("click", onMapClickClearFieldSelection);
}

function populateCropFilterOptions(geojson) {
    const select = document.getElementById("filter-crop-type");
    if (!select || !geojson?.features) return;

    const cropNames = new Set();
    geojson.features.forEach(feature => {
        parseFieldHistory(feature.properties?.history).forEach(record => {
            if (record?.cropName) cropNames.add(record.cropName);
        });
    });

    const current = select.value;
    select.innerHTML = `<option value="all">Все культуры</option>`;
    [...cropNames].sort((a, b) => a.localeCompare(b, "ru")).forEach(name => {
        const opt = document.createElement("option");
        opt.value = name;
        opt.textContent = name;
        select.appendChild(opt);
    });
    if ([...select.options].some(o => o.value === current)) {
        select.value = current;
    }
}

function updateTopAnalyticsStats(geojson) {
    if (!geojson?.features?.length) return;
    const totalFields = geojson.features.length;
    const totalArea = geojson.features.reduce((sum, feature) => {
        const area = parseFloat(feature?.properties?.area);
        return sum + (Number.isFinite(area) ? area : 0);
    }, 0);
    document.querySelectorAll(".stat-card").forEach(card => {
        const label = card.querySelector(".stat-label")?.textContent?.trim();
        const valueEl = card.querySelector(".stat-value");
        if (!label || !valueEl) return;
        if (label === "Общая площадь земель") {
            valueEl.innerHTML = `${totalArea.toFixed(1)}<span class="stat-unit">га</span>`;
        } else if (label === "Всего полей") {
            valueEl.textContent = String(totalFields);
        }
    });
}

// =====================================================
// ЗАГРУЗКА И ОТОБРАЖЕНИЕ ПОЛЕЙ
// =====================================================
async function loadFields() {
    try {
        const response = await fetch(apiPath("/get_fields"), fetchOpts);
        if (!response.ok) throw new Error();

        const data = await response.json();
        loadedFieldsGeoJson = data;
        updateTopAnalyticsStats(data);

        populateFilterFieldsList(data);
        populateCropFilterOptions(data);
        populateNdviFieldsChecklist(data);
        ensureFieldContextMenu();

        if (!map) return;

        if (map.getLayer("fields-fill")) map.removeLayer("fields-fill");
        if (map.getLayer("fields-outline")) map.removeLayer("fields-outline");
        if (map.getSource("fields-source")) map.removeSource("fields-source");

        map.addSource("fields-source", {
            type: "geojson",
            data: data,
            promoteId: "id"
        });

        map.addLayer({
            id: "fields-fill",
            type: "fill",
            source: "fields-source",
            paint: {
                "fill-color": [
                    "case",
                    ["boolean", ["feature-state", "highlight"], false], "#22c55e",
                    ["boolean", ["feature-state", "ndviSelected"], false], "#3b82f6",
                    "#10b981"
                ],
                "fill-opacity": [
                    "case",
                    ["boolean", ["feature-state", "highlight"], false], 0.38,
                    ["boolean", ["feature-state", "ndviSelected"], false], 0.38,
                    0.22
                ]
            }
        });

        map.addLayer({
            id: "fields-outline",
            type: "line",
            source: "fields-source",
            paint: {
                "line-color": [
                    "case",
                    ["boolean", ["feature-state", "highlight"], false], "#16a34a",
                    ["boolean", ["feature-state", "ndviSelected"], false], "#1d4ed8",
                    "#10b981"
                ],
                "line-width": [
                    "case",
                    ["boolean", ["feature-state", "highlight"], false], 3,
                    ["boolean", ["feature-state", "ndviSelected"], false], 3,
                    2
                ]
            }
        });

        bindFieldLayerHandlers();

        ndviSelectedFieldIds.forEach(id => setFieldFeatureState(id, { ndviSelected: true }));
        if (highlightedFieldId != null) {
            setFieldFeatureState(highlightedFieldId, { highlight: true });
        }

        map.on("mouseenter", "fields-fill", () => { if (!isMeasuring) map.getCanvas().style.cursor = "pointer"; });
        map.on("mouseleave", "fields-fill", () => { if (!isMeasuring) map.getCanvas().style.cursor = ""; });

        updateSyncStatus(true);

    } catch (e) {
        console.error("Ошибка получения полей:", e);
        updateSyncStatus(false);
    }
}

function populateFilterFieldsList(geojson) {
    const listEl = document.getElementById("filter-fields-list");
    if (!listEl || !geojson || !geojson.features) return;

    listEl.innerHTML = "";
    geojson.features.forEach(feature => {
        const props = feature.properties || {};
        const id = props.id || props.fieldId || feature.id;
        const name = props.name || props.fieldName || `Поле #${id}`;

        const label = document.createElement("label");
        label.className = "filter-checkbox-row";
        label.innerHTML = `
            <input type="checkbox" value="${id}" class="field-filter-checkbox" />
            <span>${name}</span>
        `;
        listEl.appendChild(label);
    });
}

function populateNdviFieldsChecklist(geojson) {
    const ndviBox = document.getElementById("ndvi-fields-checklist");
    const cropBox = document.getElementById("crop-fields-checklist");
    const soilBox = document.getElementById("soil-fields-checklist");

    if (!geojson || !geojson.features) return;

    ensureNdviFieldsToolbar();
    ensureCropFieldsToolbar();
    ensureSoilFieldsToolbar();

    const fillContainer = (containerEl, cssClass, checkedByDefault) => {
        if (!containerEl) return;
        containerEl.innerHTML = "";
        geojson.features.forEach(feature => {
            const props = feature.properties || {};
            const id = props.id || props.fieldId || feature.id;
            const name = props.name || props.fieldName || `Поле #${id}`;
            const isNdvi = cssClass === "ndvi-filter-checkbox";
            const isChecked = isNdvi
                ? ndviSelectedFieldIds.has(String(id))
                : checkedByDefault;
            const checkedAttr = isChecked ? ' checked="checked"' : "";

            const label = document.createElement("label");
            label.className = "filter-checkbox-row";
            const input = document.createElement("input");
            input.type = "checkbox";
            input.value = String(id);
            input.className = cssClass;
            if (isChecked) input.checked = true;
            const span = document.createElement("span");
            span.textContent = name;
            label.appendChild(input);
            label.appendChild(span);

            if (isNdvi) {
                input.addEventListener("change", () => {
                    setNdviFieldSelected(id, input.checked);
                });
            }

            containerEl.appendChild(label);
        });
    };

    fillContainer(ndviBox, "ndvi-filter-checkbox", false);
    fillContainer(cropBox, "crop-filter-checkbox", true);
    fillContainer(soilBox, "soil-filter-checkbox", true);

    initCropRecalculation();
    initSoilRecalculation();
    updateCropDistributionChart();
}

const CROP_CHART_COLORS = [
    "#10b981", "#f59e0b", "#f97316", "#3b82f6",
    "#8b5cf6", "#ec4899", "#14b8a6", "#6366f1"
];

function getLatestCropRecord(history) {
    const records = parseFieldHistory(history);
    if (!records.length) return null;
    return records.reduce((best, record) => {
        const year = record?.cropYear || 0;
        const bestYear = best?.cropYear || 0;
        return year >= bestYear ? record : best;
    }, records[0]);
}

function updateCropDistributionChart() {
    const donut = document.querySelector(".donut-chart");
    const legend = document.getElementById("crop-legend") || document.querySelector(".crop-legend");
    if (!donut || !legend) return;

    if (!loadedFieldsGeoJson?.features?.length) {
        donut.style.background = "#e2e8f0";
        donut.setAttribute("data-tip", "Нет данных");
        legend.innerHTML = `<p class="muted font-sm m-0">Загрузите поля на карту</p>`;
        return;
    }

    const selectedIds = new Set(
        Array.from(document.querySelectorAll(".crop-filter-checkbox:checked")).map(cb => String(cb.value))
    );

    const byCrop = new Map();
    loadedFieldsGeoJson.features.forEach(feature => {
        const fieldId = getFeatureFieldId(feature);
        if (selectedIds.size > 0 && !selectedIds.has(fieldId)) return;

        const props = feature.properties || {};
        const area = parseFloat(props.area) || 0;
        if (area <= 0) return;

        const latest = getLatestCropRecord(props.history);
        const cropName = (latest?.cropName || "Не указана").trim();
        byCrop.set(cropName, (byCrop.get(cropName) || 0) + area);
    });

    const entries = [...byCrop.entries()].sort((a, b) => b[1] - a[1]);
    const total = entries.reduce((sum, [, area]) => sum + area, 0);

    if (total <= 0) {
        donut.style.background = "#e2e8f0";
        donut.setAttribute("data-tip", "Нет данных по выбранным полям");
        legend.innerHTML = `<p class="muted font-sm m-0">Нет культур в истории посевов выбранных полей</p>`;
        return;
    }

    const gradientParts = [];
    const tipParts = [];
    let offset = 0;

    legend.innerHTML = "";
    entries.forEach(([name, area], index) => {
        const pct = (area / total) * 100;
        const color = CROP_CHART_COLORS[index % CROP_CHART_COLORS.length];
        gradientParts.push(`${color} ${offset}% ${offset + pct}%`);
        offset += pct;
        tipParts.push(`${name}: ${area.toFixed(1)} га`);

        const row = document.createElement("div");
        row.className = "crop-row";
        row.innerHTML = `
            <div class="crop-left">
                <span class="crop-dot" style="background:${color}" data-tip="${name}"></span>
                <span>${name}</span>
            </div>
            <span class="crop-val">${area.toFixed(1)} га</span>
        `;
        legend.appendChild(row);
    });

    donut.style.background = `conic-gradient(${gradientParts.join(", ")})`;
    donut.setAttribute("data-tip", tipParts.join(" | "));
}

// =====================================================
// ЛИНЕЙКА
// =====================================================
function setupMeasureTool() {
    if (!map) return;

    map.addSource("measure-source", {
        type: "geojson",
        data: measureGeoJson
    });

    map.addLayer({
        id: "measure-lines",
        type: "line",
        source: "measure-source",
        layout: { "line-cap": "round", "line-join": "round" },
        paint: { "line-color": "#f59e0b", "line-width": 3 }
    });

    map.addLayer({
        id: "measure-points",
        type: "circle",
        source: "measure-source",
        paint: { "circle-radius": 5, "circle-color": "#f59e0b" },
        filter: ["in", "$type", "Point"]
    });

    const measureBtn = document.getElementById("btn-measure");
    const measureTooltip = document.getElementById("measure-tooltip");

    measureBtn?.addEventListener("click", () => {
        isMeasuring = !isMeasuring;
        if (isMeasuring) {
            measureBtn.classList.add("active");
            measureBtn.style.borderColor = "#f59e0b";
            if (measureTooltip) measureTooltip.style.display = "block";
            map.getCanvas().style.cursor = "crosshair";
            resetMeasure();
        } else {
            deactivateMeasure();
        }
    });

    map.on("click", (e) => {
        if (!isMeasuring) return;
        const coords = [e.lngLat.lng, e.lngLat.lat];
        measurePoints.push(coords);
        rebuildMeasureLayers();
    });

    window.addEventListener("keydown", (e) => {
        if (e.key === "Escape" && isMeasuring) {
            deactivateMeasure();
        }
    });
}

function deactivateMeasure() {
    isMeasuring = false;
    const measureBtn = document.getElementById("btn-measure");
    const measureTooltip = document.getElementById("measure-tooltip");
    if (measureBtn) {
        measureBtn.classList.remove("active");
        measureBtn.style.borderColor = "";
    }
    if (measureTooltip) measureTooltip.style.display = "none";
    if (map) map.getCanvas().style.cursor = "";
    resetMeasure();
}

function resetMeasure() {
    measurePoints = [];
    measureGeoJson.features = [];
    if (map && map.getSource("measure-source")) {
        map.getSource("measure-source").setData(measureGeoJson);
    }
    updateMeasureOutput(0);
}

function rebuildMeasureLayers() {
    const features = [];
    let totalDist = 0;

    measurePoints.forEach((pt) => {
        features.push({
            type: "Feature",
            geometry: { type: "Point", coordinates: pt },
            properties: {}
        });
    });

    if (measurePoints.length > 1) {
        features.push({
            type: "Feature",
            geometry: { type: "LineString", coordinates: measurePoints },
            properties: {}
        });

        for (let i = 0; i < measurePoints.length - 1; i++) {
            totalDist += haversineDistance(measurePoints[i], measurePoints[i + 1]);
        }
    }

    measureGeoJson.features = features;
    if (map && map.getSource("measure-source")) {
        map.getSource("measure-source").setData(measureGeoJson);
    }
    updateMeasureOutput(totalDist);
}

function updateMeasureOutput(meters) {
    const outputEl = document.getElementById("measure-output");
    if (!outputEl) return;
    if (meters >= 1000) {
        outputEl.innerText = `Расстояние: ${(meters / 1000).toFixed(2)} км`;
    } else {
        outputEl.innerText = `Расстояние: ${Math.round(meters)} м`;
    }
}

function haversineDistance(coords1, coords2) {
    const toRad = (x) => (x * Math.PI) / 180;
    const R = 6371000;
    const dLat = toRad(coords2[1] - coords1[1]);
    const dLng = toRad(coords2[0] - coords1[0]);
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
              Math.cos(toRad(coords1[1])) * Math.cos(toRad(coords2[1])) *
              Math.sin(dLng / 2) * Math.sin(dLng / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
}

// =====================================================
// ФИЛЬТРАЦИЯ С СЕЛЕКТОРОМ ПОЛЕЙ
// =====================================================
// =====================================================
// ПАНЕЛЬ ФИЛЬТРОВ: ПРИМЕНЕНИЕ/СБРОС
// =====================================================
function setupFilterControls() {
    const btnApply = document.getElementById("btn-apply-filters");
    const btnReset = document.getElementById("btn-reset-filters");

    btnApply?.addEventListener("click", () => {
        const minArea = parseFloat(document.getElementById("filter-area-min")?.value) || 0;
        const maxAreaRaw = document.getElementById("filter-area-max")?.value;
        const maxArea = maxAreaRaw !== "" && maxAreaRaw != null ? parseFloat(maxAreaRaw) : Infinity;
        const cropType = document.getElementById("filter-crop-type")?.value || "all";
        const ndviMin = parseFloat(document.getElementById("filter-ndvi-min")?.value) || 0.0;
        const ndviMax = parseFloat(document.getElementById("filter-ndvi-max")?.value) || 1.0;
        const statusLevel = document.getElementById("filter-status-level")?.value || "all";

        const checkedBoxes = document.querySelectorAll(".field-filter-checkbox:checked");
        const selectedFieldIds = Array.from(checkedBoxes).map(cb => String(cb.value));

        if (!loadedFieldsGeoJson || !map) return;

        const filteredFeatures = loadedFieldsGeoJson.features.filter(feature => {
            const props = feature.properties || {};
            const id = props.id || props.fieldId || feature.id;
            const area = parseFloat(props.area) || 0;
            const history = parseFieldHistory(props.history);
            const ndviVal = props.ndvi_mean || 0.65;

            let fieldMatch = true;
            if (selectedFieldIds.length > 0) {
                fieldMatch = selectedFieldIds.includes(String(id));
            }

            const areaOk = area >= minArea && area <= maxArea;

            let cropOk = true;
            if (cropType !== "all") {
                const latestCrop = getLatestCropRecord(history);
                cropOk = latestCrop?.cropName === cropType;
            }

            const ndviOk = ndviVal >= ndviMin && ndviVal <= ndviMax;

            let statusOk = true;
            if (statusLevel === "high") {
                statusOk = ndviVal >= 0.7;
            } else if (statusLevel === "mid") {
                statusOk = ndviVal >= 0.4 && ndviVal < 0.7;
            } else if (statusLevel === "low") {
                statusOk = ndviVal < 0.4;
            }

            return fieldMatch && areaOk && cropOk && ndviOk && statusOk;
        });

        map.getSource("fields-source")?.setData({
            type: "FeatureCollection",
            features: filteredFeatures
        });
        ndviSelectedFieldIds.forEach(id => setFieldFeatureState(id, { ndviSelected: true }));
        if (highlightedFieldId != null) {
            setFieldFeatureState(highlightedFieldId, { highlight: true });
        }
    });

    btnReset?.addEventListener("click", () => {
        const areaMinEl = document.getElementById("filter-area-min");
        const areaMaxEl = document.getElementById("filter-area-max");
        const cropTypeEl = document.getElementById("filter-crop-type");
        const ndviMinEl = document.getElementById("filter-ndvi-min");
        const ndviMaxEl = document.getElementById("filter-ndvi-max");
        const statusLevelEl = document.getElementById("filter-status-level");

        if (areaMinEl) areaMinEl.value = "";
        if (areaMaxEl) areaMaxEl.value = "";
        if (cropTypeEl) cropTypeEl.value = "all";
        if (ndviMinEl) ndviMinEl.value = "";
        if (ndviMaxEl) ndviMaxEl.value = "";
        if (statusLevelEl) statusLevelEl.value = "all";

        document.querySelectorAll(".field-filter-checkbox").forEach(cb => {
            cb.checked = false;
        });

        if (loadedFieldsGeoJson && map) {
            map.getSource("fields-source")?.setData(loadedFieldsGeoJson);
        }
        applyFieldHighlight(null);
    });
}

// =====================================================
// ИМПОРТ ДАННЫХ
// =====================================================
// =====================================================
// МОДАЛКА ИМПОРТА ФАЙЛОВ
// =====================================================
function setupImportModal() {
    const modal = document.getElementById("import-modal");
    const dropzone = document.getElementById("dropzone");
    const fileInput = document.getElementById("file-input");
    const preview = document.getElementById("import-preview");
    const fileName = document.getElementById("selected-file-name");
    const btnSubmit = document.getElementById("btn-submit-import");

    const closeActions = [
        document.getElementById("btn-close-import"),
        document.getElementById("btn-cancel-import")
    ];

    closeActions.forEach(btn => {
        btn?.addEventListener("click", () => {
            modal?.classList.remove("open");
            resetImportState();
        });
    });

    dropzone?.addEventListener("click", () => fileInput?.click());

    dropzone?.addEventListener("dragover", (e) => {
        e.preventDefault();
        dropzone.classList.add("dragover");
    });

    dropzone?.addEventListener("dragleave", () => {
        dropzone.classList.remove("dragover");
    });

    dropzone?.addEventListener("drop", (e) => {
        e.preventDefault();
        dropzone.classList.remove("dragover");
        if (e.dataTransfer.files.length > 0) {
            handleFileSelection(e.dataTransfer.files[0]);
        }
    });

    fileInput?.addEventListener("change", (e) => {
        if (e.target.files.length > 0) {
            handleFileSelection(e.target.files[0]);
        }
    });

    function handleFileSelection(file) {
        if (fileName) fileName.textContent = `${file.name} (${(file.size / 1024).toFixed(1)} Кб)`;
        if (preview) preview.style.display = "block";
        btnSubmit?.removeAttribute("disabled");
    }

    function resetImportState() {
        if (fileInput) fileInput.value = "";
        if (preview) preview.style.display = "none";
        if (fileName) fileName.textContent = "—";
        btnSubmit?.setAttribute("disabled", "true");
    }

    btnSubmit?.addEventListener("click", () => {
        alert("Данные подготовлены для импорта.");
        modal?.classList.remove("open");
        resetImportState();
    });
}

// =====================================================
// НАВИГАЦИЯ И БОКОВЫЕ ПАНЕЛИ
// =====================================================
// =====================================================
// БОКОВЫЕ ПАНЕЛИ И КНОПКИ ИНТЕРФЕЙСА
// =====================================================
function setupSidebarAndPanels() {
    document.getElementById("sidebar-toggle")?.addEventListener("click", () => {
        document.getElementById("sidebar")?.classList.toggle("open");
    });

    document.getElementById("analytics-toggle")?.addEventListener("click", () => {
        document.getElementById("analytics-panel")?.classList.add("open");
        syncFieldHistoryPanelLayout();
    });

    document.getElementById("analytics-close")?.addEventListener("click", () => {
        document.getElementById("analytics-panel")?.classList.remove("open");
        syncFieldHistoryPanelLayout();
    });

    setupAnalyticsPanelResize();
    ensureFieldHistoryPanel();

    document.getElementById("layers-toggle")?.addEventListener("click", () => {
        document.getElementById("layers-panel")?.classList.toggle("open");
    });

    document.getElementById("filter-toggle")?.addEventListener("click", () => {
        document.getElementById("filter-panel")?.classList.toggle("open");
    });

    document.getElementById("btn-fit-bounds")?.addEventListener("click", () => {
        if (!map) return;
        if (!loadedFieldsGeoJson || !loadedFieldsGeoJson.features || loadedFieldsGeoJson.features.length === 0) {
            map.flyTo({ center: SARATOV_CENTER, zoom: SARATOV_ZOOM });
            return;
        }
        const bounds = new maplibregl.LngLatBounds();
        loadedFieldsGeoJson.features.forEach(feature => {
            expandBounds(bounds, feature.geometry);
        });
        map.fitBounds(bounds, { padding: 80, duration: 1200 });
    });
}

let analyticsResizeBound = false;

function setupAnalyticsPanelResize() {
    const panel = document.getElementById("analytics-panel");
    const handle = document.getElementById("analytics-resize-handle");
    if (!panel || !handle || analyticsResizeBound) return;
    analyticsResizeBound = true;

    const applySavedWidth = () => {
        const savedWidth = Number(localStorage.getItem(ANALYTICS_PANEL_WIDTH_KEY));
        if (savedWidth >= ANALYTICS_PANEL_MIN_WIDTH) {
            panel.style.setProperty("--analytics-panel-width", `${savedWidth}px`);
            panel.style.width = `${savedWidth}px`;
        }
    };
    applySavedWidth();

    const maxWidth = () =>
        Math.min(window.innerWidth * ANALYTICS_PANEL_MAX_WIDTH_RATIO, 720);

    const applyWidth = (clientX) => {
        const width = Math.min(maxWidth(), Math.max(ANALYTICS_PANEL_MIN_WIDTH, window.innerWidth - clientX));
        panel.style.setProperty("--analytics-panel-width", `${width}px`);
        panel.style.width = `${width}px`;
        syncFieldHistoryPanelLayout();
        return width;
    };

    let dragging = false;
    let activePointerId = null;

    const stopDrag = (e) => {
        if (!dragging) return;
        if (e?.pointerId != null && activePointerId != null && e.pointerId !== activePointerId) return;
        dragging = false;
        activePointerId = null;
        handle.classList.remove("is-dragging");
        panel.classList.remove("is-resizing");
        document.body.style.cursor = "";
        document.body.style.userSelect = "";
        try {
            if (e?.pointerId != null) handle.releasePointerCapture(e.pointerId);
        } catch (_) { /* already released */ }
        const width = panel.getBoundingClientRect().width;
        localStorage.setItem(ANALYTICS_PANEL_WIDTH_KEY, String(Math.round(width)));
        syncFieldHistoryPanelLayout();
    };

    const onPointerMove = (e) => {
        if (!dragging) return;
        e.preventDefault();
        applyWidth(e.clientX);
    };

    const startDrag = (e) => {
        if (!panel.classList.contains("open")) return;
        if (e.button !== undefined && e.button !== 0) return;
        e.preventDefault();
        e.stopPropagation();
        dragging = true;
        activePointerId = e.pointerId;
        handle.classList.add("is-dragging");
        panel.classList.add("is-resizing");
        document.body.style.cursor = "ew-resize";
        document.body.style.userSelect = "none";
        try {
            handle.setPointerCapture(e.pointerId);
        } catch (_) { /* fallback to document listeners */ }
        applyWidth(e.clientX);
    };

    handle.addEventListener("pointerdown", startDrag);
    handle.addEventListener("pointermove", onPointerMove);
    handle.addEventListener("pointerup", stopDrag);
    handle.addEventListener("pointercancel", stopDrag);
    document.addEventListener("pointermove", onPointerMove);
    document.addEventListener("pointerup", stopDrag);
}

// =====================================================
// ПАНЕЛЬ СЛОЕВ КАРТЫ
// =====================================================
function setupLayerControls() {
    document.querySelectorAll('input[name="base-layer"]').forEach(input => {
        input.addEventListener("change", (e) => {
            const value = e.target.value;
            if (!map) return;
            map.setLayoutProperty("google-satellite", "visibility", value === "google" ? "visible" : "none");
            map.setLayoutProperty("osm", "visibility", value === "osm" ? "visible" : "none");
        });
    });

    const toggleFields = document.getElementById("toggle-fields");
    toggleFields?.addEventListener("change", (e) => {
        const visibility = e.target.checked ? "visible" : "none";
        if (!map) return;
        if (map.getLayer("fields-fill")) map.setLayoutProperty("fields-fill", "visibility", visibility);
        if (map.getLayer("fields-outline")) map.setLayoutProperty("fields-outline", "visibility", visibility);
    });

    const ndviLayersToolbar = document.getElementById("ndvi-layers-toolbar");
    ndviLayersToolbar?.querySelector("[data-action='all']")?.addEventListener("click", () => {
        document.querySelectorAll("#dynamic-layers-container input[type='checkbox']").forEach(cb => {
            cb.checked = true;
            cb.dispatchEvent(new Event("change", { bubbles: true }));
        });
    });
    ndviLayersToolbar?.querySelector("[data-action='none']")?.addEventListener("click", () => {
        document.querySelectorAll("#dynamic-layers-container input[type='checkbox']").forEach(cb => {
            cb.checked = false;
            cb.dispatchEvent(new Event("change", { bubbles: true }));
        });
    });
}

// =====================================================
// ПЕРЕКЛЮЧЕНИЕ ТАБОВ АНАЛИТИКИ (ВЕГЕТАЦИЯ / ПОГОДА)
// =====================================================
// =====================================================
// ВКЛАДКИ АНАЛИТИКИ (ВЕГЕТАЦИЯ / ПОГОДА)
// =====================================================
function setupAnalyticsTabs() {
    const tabs = Array.from(document.querySelectorAll(".analytics-tab"));
    const vegContent = document.getElementById("tab-content-veg");
    const weatherContent = document.getElementById("tab-content-weather");
    if (!tabs.length || !vegContent || !weatherContent) return;

    // Защита от некорректной HTML-вложенности: weather не должен быть дочерним блоком veg.
    if (vegContent.contains(weatherContent)) {
        vegContent.insertAdjacentElement("afterend", weatherContent);
        console.warn("[AnalyticsTabs] fixed malformed DOM: moved weather tab out of veg container");
    }

    // Централизованный переключатель вкладок, чтобы состояние не "расходилось" между кнопками и контентом.
    const activateTab = (tabName) => {
        const name = tabName === "weather" ? "weather" : "veg";
        tabs.forEach(t => t.classList.toggle("active", t.getAttribute("data-tab") === name));
        vegContent.classList.toggle("tab-content-hidden", name !== "veg");
        weatherContent.classList.toggle("tab-content-hidden", name !== "weather");
        // Жёстко фиксируем видимость через inline-стиль, чтобы избежать конфликтов каскада CSS.
        vegContent.style.display = name === "veg" ? "block" : "none";
        weatherContent.style.display = name === "weather" ? "block" : "none";
        console.info("[AnalyticsTabs] activate", {
            tab: name,
            weatherChildren: weatherContent.children.length,
            weatherHtmlLength: weatherContent.innerHTML?.length || 0,
            weatherDisplay: getComputedStyle(weatherContent).display
        });
        if (name === "weather" && document.getElementById("analytics-panel")?.classList.contains("open")) {
            requestAnimationFrame(() => ensureWeatherTabHasVisibleContent(weatherContent));
        }
    };

    tabs.forEach(tab => {
        tab.addEventListener("click", () => activateTab(tab.getAttribute("data-tab")));
    });

    const initiallyActive = tabs.find(t => t.classList.contains("active"))?.getAttribute("data-tab");
    activateTab(initiallyActive || "veg");

    // Дополнительный страховочный обработчик: если вкладки перерисовались, клик всё равно переключит контент.
    document.addEventListener("click", (e) => {
        const tab = e.target.closest(".analytics-tab");
        if (!tab) return;
        activateTab(tab.getAttribute("data-tab"));
    });
}

function ensureWeatherTabFallbackContent() {
    const weather = document.getElementById("tab-content-weather");
    if (!weather) return;
    const hasRealContent = weather.querySelector(".analytics-card, .stats-grid, .stat-card");
    if (hasRealContent) return;
    weather.innerHTML = `
        <div class="stats-grid">
            <div class="stat-card">
                <div class="stat-left">
                    <div class="stat-label">Температура</div>
                    <div class="stat-value">+22<span class="stat-unit">°C</span></div>
                </div>
                <div class="stat-right icon-orange">☀️</div>
            </div>
            <div class="stat-card">
                <div class="stat-left">
                    <div class="stat-label">Влажность</div>
                    <div class="stat-value">48<span class="stat-unit">%</span></div>
                </div>
                <div class="stat-right icon-blue">💧</div>
            </div>
        </div>
        <div class="analytics-card">
            <div class="card-title-mini">Осадки за 7 дней (заглушка)</div>
            <p class="muted font-sm m-0">Данные погоды временно недоступны. Отображается демо-блок.</p>
        </div>
    `;
}

function ensureWeatherTabHasVisibleContent(weatherContent) {
    const hasVisibleBlocks = weatherContent.querySelector(".stats-grid, .analytics-card, .stat-card");
    const hasVisibleHeight = weatherContent.getBoundingClientRect().height > 24;
    if (hasVisibleBlocks && hasVisibleHeight) {
        return;
    }
    console.warn("[AnalyticsTabs] weather tab has no visible content, applying fallback", {
        hasVisibleBlocks: !!hasVisibleBlocks,
        height: weatherContent.getBoundingClientRect().height
    });
    ensureWeatherTabFallbackContent();
}

// =====================================================
// ИНТЕРАКТИВНЫЙ РАСЧЕТ NDVI ГРУППЫ ПОЛЕЙ (UX)
// =====================================================
function setupNdviCalculation() {
    const btnCalc = document.getElementById("btn-calculate-ndvi");
    const startDateInput = document.getElementById("ndvi-start-date");
    const endDateInput = document.getElementById("ndvi-end-date");

    btnCalc?.addEventListener("click", async () => {
        const checkedBoxes = document.querySelectorAll(".ndvi-filter-checkbox:checked");
        const selectedFieldIds = Array.from(checkedBoxes).map(cb => String(cb.value));

        const startDate = startDateInput?.value;
        const endDate = endDateInput?.value || startDate;

        if (selectedFieldIds.length === 0) {
            alert("Пожалуйста, отметьте галочками поля для расчёта NDVI.");
            return;
        }
        if (!startDate) {
            alert("Пожалуйста, выберите 'Дату начала'.");
            return;
        }
        if (endDate && startDate > endDate) {
            alert("Дата начала не может быть больше даты конца.");
            return;
        }

        const trendTitle = document.getElementById("trend-title-ref");
        if (trendTitle) {
            trendTitle.innerHTML = `<span class="loading-inline"><span class="loading-spinner"></span>Загрузка снимков...</span>`;
        }

        // 1. ПОЛНОСТЬЮ ОЧИЩАЕМ КАРТУ И МЕНЮ ОТ СТАРЫХ РАСТРОВ
        clearNdviLayers();

        try {
            // =====================================================
            // NDVI: ЗАГРУЗКА ТАЙЛОВ ЧЕРЕЗ JAVA API
            // =====================================================
            // 2. ЗАГРУЖАЕМ РАСТРЫ ДЛЯ КАЖДОГО ВЫБРАННОГО ПОЛЯ
            const tilePromises = selectedFieldIds.map(fieldId => {
                return fetch(apiPath(`/get_ndvi_tiles_for_field?field_id=${fieldId}&date=${startDate}`), fetchOpts)
                    .then(res => {
                        if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
                        return res.json();
                    })
                    .then(data => ({ fieldId, data }))
                    .catch(err => {
                        console.error(`Не удалось получить тайлы для поля ${fieldId}:`, err);
                        return null;
                    });
            });

            const tileResults = await Promise.all(tilePromises);

            // 3. ДОБАВЛЯЕМ СЛОИ НА КАРТУ И В МЕНЮ
            let loadedCount = 0;
            tileResults.forEach(result => {
                if (result && result.data && result.data.url) {
                    const layerId = `ndvi-layer-${result.fieldId}`;

                    // Добавляем на карту
                    addNdviLayer(result.data.url, layerId, result.fieldId);

                    // Добавляем переключатель в панель слоев
                    addLayerToggleToMenu(
                        layerId,
                        `NDVI Поле #${result.fieldId} (${result.data.actual_date})`,
                        {
                            containerId: "dynamic-layers-container",
                            blockId: "ndvi-layers-block",
                            onVisibilityChange: refreshNdviLegendVisibility
                        }
                    );

                    activeNdviLayerIds.push(layerId);
                    loadedCount++;
                }
            });

            if (trendTitle) trendTitle.textContent = `Отображено растров: ${loadedCount} из ${selectedFieldIds.length}`;

            // =====================================================
            // NDVI: ЗАГРУЗКА ТРЕНДА ЧЕРЕЗ JAVA API
            // =====================================================
            // 4. ГРАФИК ТРЕНДА (Вторичная задача)
            try {
                const trendQuery = `${selectedFieldIds.map(id => `field_id=${id}`).join('&')}&start_date=${startDate}&end_date=${endDate}&warmup=true`;
                const trendResponse = await fetch(apiPath(`/get_ndvi_trend?${trendQuery}`), fetchOpts);
                if (trendResponse.ok) {
                    const trendData = await trendResponse.json();
                    updateNdviChart(trendData.labels, trendData.data);
                    const avgNdviEl = document.getElementById("calc-avg-ndvi");
                    if (avgNdviEl && trendData.data.length > 0) {
                        avgNdviEl.textContent = (trendData.data.reduce((a, b) => a + b, 0) / trendData.data.length).toFixed(2);
                    } else if (avgNdviEl) {
                        avgNdviEl.textContent = "—";
                    }
                    if (trendTitle) {
                        trendTitle.textContent = trendData.data.length > 0
                            ? `NDVI: ${startDate} - ${endDate} (${trendData.data.length} т.)`
                            : "Нет NDVI-аналитики за выбранный период";
                    }
                }
            } catch (trendErr) {
                console.warn("График тренда не загрузился", trendErr);
            }

        } catch (error) {
            console.error("Общая ошибка расчета:", error);
            alert(`Ошибка: ${error.message}`);
            if (trendTitle) trendTitle.textContent = `Ошибка загрузки`;
        }
    });
}

// =====================================================
// КАРТОЧКА АНАЛИТИКИ УКЛОНОВ
// =====================================================
function setupSlopeAnalyticsCard() {
    const vegTab = document.getElementById("tab-content-veg");
    if (!vegTab || document.getElementById("slope-card")) return;
    const card = document.createElement("div");
    card.className = "analytics-card";
    card.id = "slope-card";
    card.innerHTML = `
        <div class="card-title-mini">Уклоны (Slope)</div>
        <div class="filter-group">
            <div class="filter-label">Используются выбранные поля и период из блока NDVI</div>
            <button type="button" class="filter-btn primary w-100-pct" id="btn-calculate-slope">Рассчитать уклоны</button>
        </div>
        <div class="chart-container mt-2" id="slope-chart-box">
            <svg viewBox="0 0 320 160" width="100%" height="150" class="chart-svg-visible">
                <line x1="30" y1="10" x2="30" y2="130" stroke="#e2e8f0" stroke-width="1.5"></line>
                <line x1="30" y1="130" x2="310" y2="130" stroke="#94a3b8" stroke-width="1.5"></line>
                <path id="slope-path-line" d="" fill="none" stroke="#2563eb" stroke-width="3" stroke-linecap="round"></path>
            </svg>
        </div>
        <p class="muted font-sm m-0" id="slope-status-text">Нет данных</p>
    `;
    // Карточка "Уклоны" должна стоять сразу под NDVI-трендом.
    const ndviTrendCard = document.getElementById("trend-chart-box")?.closest(".analytics-card");
    if (ndviTrendCard?.parentElement) {
        ndviTrendCard.insertAdjacentElement("afterend", card);
    } else {
        vegTab.appendChild(card);
    }
}

// =====================================================
// РАСЧЕТ И ЗАГРУЗКА ТРЕНДА УКЛОНОВ
// =====================================================
function setupSlopeCalculation() {
    const btn = document.getElementById("btn-calculate-slope");
    btn?.addEventListener("click", async () => {
        const selectedFieldIds = Array.from(document.querySelectorAll(".ndvi-filter-checkbox:checked")).map(cb => String(cb.value));
        const startDate = document.getElementById("ndvi-start-date")?.value;
        const endDate = document.getElementById("ndvi-end-date")?.value || startDate;
        const status = document.getElementById("slope-status-text");
        if (selectedFieldIds.length === 0) {
            if (status) status.textContent = "Выберите поля для расчёта уклонов";
            return;
        }
        if (!startDate) {
            if (status) status.textContent = "Укажите дату начала";
            return;
        }
        if (status) {
            status.innerHTML = `<span class="loading-inline"><span class="loading-spinner"></span>Загрузка данных уклонов...</span>`;
        }
        // =====================================================
        // SLOPE: ПРОГРЕВ ТАЙЛОВ ЧЕРЕЗ JAVA API
        // =====================================================
        // Прогрев slope-тайлов по выбранным полям перед запросом тренда (аналог потока NDVI).
        const tilePromises = selectedFieldIds.map(fieldId =>
            fetch(apiPath(`/get_slope_tiles_for_field?field_id=${fieldId}&date=${startDate}`), fetchOpts).catch(() => null)
        );
        await Promise.all(tilePromises);
        // =====================================================
        // SLOPE: ТРЕНД ЧЕРЕЗ JAVA API
        // =====================================================
        const trendQuery = `${selectedFieldIds.map(id => `field_id=${id}`).join("&")}&start_date=${startDate}&end_date=${endDate}`;
        const trendResponse = await fetch(apiPath(`/get_slope_trend?${trendQuery}`), fetchOpts);
        if (!trendResponse.ok) {
            let message = "Не удалось получить тренд уклонов";
            try {
                const err = await trendResponse.json();
                message = err?.error || err?.detail || message;
            } catch (_) { /* ignore parse errors */ }
            if (status) status.textContent = message;
            return;
        }
        const trendData = await trendResponse.json();
        updateSlopeChart(trendData.labels || [], trendData.data || []);
        if (status) status.textContent = trendData.data?.length ? `Точек тренда: ${trendData.data.length}` : "Нет данных за период";
    });
}

// =====================================================
// ОТРИСОВКА ГРАФИКА УКЛОНОВ
// =====================================================
function updateSlopeChart(labels, data) {
    const svgContainer = document.querySelector("#slope-chart-box svg");
    const pathEl = document.getElementById("slope-path-line");
    if (!svgContainer || !pathEl) return;
    document.querySelectorAll(".dynamic-slope-node").forEach(el => el.remove());
    if (!data || data.length === 0) {
        pathEl.setAttribute("d", "");
        return;
    }
    // Масштаб Y строим от фактического диапазона уклонов в ответе.
    const min = Math.min(...data);
    const max = Math.max(...data);
    const span = Math.max(0.1, max - min);
    const scaleY = (val) => 20 + (110 - ((val - min) / span) * 100);
    const stepX = (320 - 60) / Math.max(1, data.length - 1);
    let d = "";
    data.forEach((val, i) => {
        const x = 30 + i * stepX;
        const y = scaleY(val);
        d += (i === 0 ? `M ${x},${y}` : ` L ${x},${y}`);
        const circle = document.createElementNS("http://www.w3.org/2000/svg", "circle");
        circle.setAttribute("cx", x);
        circle.setAttribute("cy", y);
        circle.setAttribute("r", 4.5);
        circle.setAttribute("fill", "#2563eb");
        circle.setAttribute("class", "dynamic-slope-node");
        circle.setAttribute("data-tip", `${labels[i] || i + 1}: ${Number(val).toFixed(2)}°`);
        svgContainer.appendChild(circle);
    });
    pathEl.setAttribute("d", d);
}

function setupAnalyticsCardCollapsing() {
    // Унифицированное сворачивание карточек аналитики через заголовок.
    document.querySelectorAll(".analytics-card").forEach((card, idx) => {
        if (card.dataset.collapseBound === "1") return;
        const title = card.querySelector(".card-title-mini");
        if (!title) return;
        card.dataset.collapseBound = "1";
        const titleText = title.textContent || `Блок ${idx + 1}`;
        title.innerHTML = `
            <button type="button" class="analytics-collapse-btn" aria-expanded="true">
                <span>${titleText}</span><span class="analytics-collapse-icon">▾</span>
            </button>
        `;
        const btn = title.querySelector(".analytics-collapse-btn");
        btn?.addEventListener("click", () => {
            const collapsed = card.classList.toggle("analytics-card-collapsed");
            btn.setAttribute("aria-expanded", String(!collapsed));
        });
    });
}

// Функция добавления слоя на карту
// =====================================================
// NDVI: ДОБАВЛЕНИЕ РАСТРОВОГО СЛОЯ НА КАРТУ
// =====================================================
// =====================================================
// РЕЛЬЕФ / УКЛОН: ПОДГОТОВКА И ОТОБРАЖЕНИЕ НА КАРТЕ
// =====================================================
async function prepareFieldTerrain(fieldId) {
    const idStr = String(fieldId);
    const response = await fetch(pythonApiPath(`/prepare_elevation/${idStr}`), pythonFetchOpts);
    if (!response.ok) {
        let detail = `HTTP ${response.status}`;
        try {
            const errBody = await response.json();
            detail = errBody.detail || errBody.error || detail;
        } catch (_) { /* ignore */ }
        throw new Error(detail);
    }
    const payload = await response.json();
    if (payload.status !== "ready" || !payload.tile_url) {
        throw new Error("Сервер не подготовил данные рельефа");
    }
    if (payload.elevation_min != null && payload.elevation_max != null) {
        elevationRangeByFieldId.set(idStr, {
            min: Number(payload.elevation_min),
            max: Number(payload.elevation_max)
        });
    }
    return payload;
}

function removeRasterLayer(sourceId, layerId) {
    if (!map) return;
    if (map.getLayer(layerId)) map.removeLayer(layerId);
    if (map.getSource(sourceId)) map.removeSource(sourceId);
}

function addRasterLayer(tileUrlTemplate, sourceId, layerId, fieldId) {
    const source = { type: "raster", tiles: [tileUrlTemplate], tileSize: 256 };
    const bounds = getFieldBoundsById(String(fieldId));
    if (bounds) source.bounds = bounds;
    map.addSource(sourceId, source);
    map.addLayer(
        {
            id: layerId,
            type: "raster",
            source: sourceId,
            paint: { "raster-opacity": 0.8 }
        },
        "fields-outline"
    );
}

function isLayerVisibleOnMap(layerId) {
    if (!map || !map.getLayer(layerId)) return false;
    const visibility = map.getLayoutProperty(layerId, "visibility");
    return visibility !== "none";
}

function updateLayersBlockEmptyState(blockId, containerId) {
    const block = document.getElementById(blockId);
    const container = document.getElementById(containerId);
    if (!block || !container) return;
    block.classList.toggle("is-empty", container.children.length === 0);
}

function layoutMapLegends() {
    const elevLegend = document.getElementById("elevation-map-legend");
    const ndviLegend = document.getElementById("ndvi-map-legend");
    if (!elevLegend) return;
    const ndviVisible = ndviLegend && !ndviLegend.classList.contains("ndvi-map-legend-hidden");
    elevLegend.style.bottom = ndviVisible ? "108px" : "24px";
}

async function runTerrainButtonAction(buttonElement, originalLabel, action) {
    if (buttonElement) {
        buttonElement.disabled = true;
        buttonElement.textContent = "⏳ Подготовка данных...";
    }
    try {
        await action();
        if (buttonElement) {
            buttonElement.textContent = "✅ Готово";
            setTimeout(() => {
                buttonElement.style.display = "none";
            }, 2000);
        }
    } catch (error) {
        if (buttonElement) {
            buttonElement.disabled = false;
            buttonElement.textContent = originalLabel;
        }
        throw error;
    }
}

/**
 * Lazy-loading рельефа: prepare → raster source → слой + пункт в «Слои карты».
 */
async function showFieldElevation(fieldId, buttonElement) {
    if (!map) {
        console.error("showFieldElevation: карта не инициализирована");
        return;
    }

    const idStr = String(fieldId);
    const sourceId = `elevation-source-${idStr}`;
    const layerId = `elevation-layer-${idStr}`;
    const originalLabel = buttonElement?.textContent || "🏔️ Показать рельеф (SRTM)";

    try {
        await runTerrainButtonAction(buttonElement, originalLabel, async () => {
            const payload = await prepareFieldTerrain(idStr);
            removeRasterLayer(sourceId, layerId);
            addRasterLayer(payload.tile_url, sourceId, layerId, idStr);

            if (!activeElevationLayerIds.includes(layerId)) {
                activeElevationLayerIds.push(layerId);
            }

            if (!document.getElementById(`toggle-${layerId}`)) {
                addLayerToggleToMenu(layerId, `Рельеф поля #${idStr}`, {
                    containerId: "dynamic-elevation-layers-container",
                    blockId: "elevation-layers-block",
                    rowClass: "dynamic-elevation-toggle",
                    onVisibilityChange: refreshElevationLegendVisibility
                });
            }

            refreshElevationLegendVisibility();
        });
    } catch (error) {
        console.error("showFieldElevation:", error);
        alert(`Не удалось загрузить рельеф: ${error.message}`);
    }
}

/**
 * Уклон: тот же prepare (elevation + slope.tif), тайлы /tiles/slope/...
 */
async function showFieldSlope(fieldId, buttonElement) {
    if (!map) {
        console.error("showFieldSlope: карта не инициализирована");
        return;
    }

    const idStr = String(fieldId);
    const sourceId = `slope-source-${idStr}`;
    const layerId = `slope-layer-${idStr}`;
    const originalLabel = buttonElement?.textContent || "📐 Показать уклон";

    try {
        await runTerrainButtonAction(buttonElement, originalLabel, async () => {
            const payload = await prepareFieldTerrain(idStr);
            const slopeUrl = payload.slope_tile_url;
            if (!slopeUrl) {
                throw new Error("Сервер не вернул URL тайлов уклона");
            }

            removeRasterLayer(sourceId, layerId);
            addRasterLayer(slopeUrl, sourceId, layerId, idStr);

            if (!activeSlopeLayerIds.includes(layerId)) {
                activeSlopeLayerIds.push(layerId);
            }

            if (!document.getElementById(`toggle-${layerId}`)) {
                addLayerToggleToMenu(layerId, `Уклон поля #${idStr}`, {
                    containerId: "dynamic-slope-layers-container",
                    blockId: "slope-layers-block",
                    rowClass: "dynamic-slope-toggle",
                    onVisibilityChange: () => updateLayersBlockEmptyState("slope-layers-block", "dynamic-slope-layers-container")
                });
            }
        });
    } catch (error) {
        console.error("showFieldSlope:", error);
        alert(`Не удалось загрузить уклон: ${error.message}`);
    }
}

function addNdviLayer(tileUrlTemplate, layerId, fieldId) {
    if (!map) return;
    if (map.getLayer(layerId)) {
        map.removeLayer(layerId);
        if (map.getSource(layerId)) map.removeSource(layerId);
    }
    const source = { type: "raster", tiles: [tileUrlTemplate], tileSize: 256 };
    const bounds = getFieldBoundsById(fieldId);
    if (bounds) {
        source.bounds = bounds;
    }
    map.addSource(layerId, source);
    map.addLayer({
        id: layerId, type: "raster", source: layerId,
        paint: { "raster-opacity": 0.8 }
    }, "fields-outline");
    refreshNdviLegendVisibility();
}

// =====================================================
// ПЕРЕКЛЮЧАТЕЛЬ РАСТРОВОГО СЛОЯ В ПАНЕЛИ «СЛОИ КАРТЫ»
// =====================================================
function addLayerToggleToMenu(layerId, label, options = {}) {
    const containerId = options.containerId || "dynamic-layers-container";
    const blockId = options.blockId || "ndvi-layers-block";
    const rowClass = options.rowClass || "dynamic-ndvi-toggle";
    const dynamicContainer = document.getElementById(containerId);
    if (!dynamicContainer) return;

    if (document.getElementById(`toggle-${layerId}`)) {
        updateLayersBlockEmptyState(blockId, containerId);
        return;
    }

    const labelRow = document.createElement("label");
    labelRow.className = `filter-checkbox-row ${rowClass}`;
    labelRow.id = `toggle-${layerId}`;

    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.checked = true;

    const span = document.createElement("span");
    span.textContent = label;

    checkbox.addEventListener("change", (e) => {
        if (map && map.getLayer(layerId)) {
            map.setLayoutProperty(layerId, "visibility", e.target.checked ? "visible" : "none");
        }
        if (typeof options.onVisibilityChange === "function") {
            options.onVisibilityChange();
        }
    });

    labelRow.appendChild(checkbox);
    labelRow.appendChild(span);
    dynamicContainer.appendChild(labelRow);
    updateLayersBlockEmptyState(blockId, containerId);
}

// НОВАЯ ФУНКЦИЯ: Очищает слои с карты и удаляет их из меню
// =====================================================
// NDVI: ОЧИСТКА СЛОЕВ И ПЕРЕКЛЮЧАТЕЛЕЙ
// =====================================================
function clearNdviLayers() {
    activeNdviLayerIds.forEach(layerId => {
        // Удаляем с карты
        if (map && map.getLayer(layerId)) {
            map.removeLayer(layerId);
            if (map.getSource(layerId)) map.removeSource(layerId);
        }
        // Удаляем чекбокс из меню
        const toggleEl = document.getElementById(`toggle-${layerId}`);
        if (toggleEl) toggleEl.remove();
    });
    activeNdviLayerIds = [];
    refreshNdviLegendVisibility();
}

function refreshNdviLegendVisibility() {
    const anyVisible = activeNdviLayerIds.some(id => isLayerVisibleOnMap(id));
    setNdviLegendVisible(anyVisible);
    layoutMapLegends();
}

// =====================================================
// РЕЛЬЕФ: ЛЕГЕНДА НА КАРТЕ
// =====================================================
function ensureElevationLegend() {
    if (document.getElementById("elevation-map-legend")) return;
    const legend = document.createElement("div");
    legend.id = "elevation-map-legend";
    legend.className = "elevation-map-legend elevation-map-legend-hidden";
    legend.innerHTML = `
        <div class="elevation-map-legend-title">Легенда рельефа (м)</div>
        <div class="elevation-map-legend-bar"></div>
        <div class="elevation-map-legend-labels" id="elevation-legend-labels">
            <span id="elevation-legend-min">—</span>
            <span id="elevation-legend-max">—</span>
        </div>
        <div class="elevation-map-legend-hint">Шкала по видимым слоям рельефа</div>
    `;
    document.body.appendChild(legend);
}

function updateElevationLegendLabels() {
    const minEl = document.getElementById("elevation-legend-min");
    const maxEl = document.getElementById("elevation-legend-max");
    if (!minEl || !maxEl) return;

    let minVal = Infinity;
    let maxVal = -Infinity;
    activeElevationLayerIds.forEach(layerId => {
        if (!isLayerVisibleOnMap(layerId)) return;
        const fieldId = layerId.replace("elevation-layer-", "");
        const range = elevationRangeByFieldId.get(fieldId);
        if (!range) return;
        minVal = Math.min(minVal, range.min);
        maxVal = Math.max(maxVal, range.max);
    });

    if (!Number.isFinite(minVal) || !Number.isFinite(maxVal)) {
        minEl.textContent = "—";
        maxEl.textContent = "—";
        return;
    }
    minEl.textContent = `${Math.round(minVal)} м`;
    maxEl.textContent = `${Math.round(maxVal)} м`;
}

function refreshElevationLegendVisibility() {
    const anyVisible = activeElevationLayerIds.some(id => isLayerVisibleOnMap(id));
    setElevationLegendVisible(anyVisible);
    updateElevationLegendLabels();
    layoutMapLegends();
}

function setElevationLegendVisible(visible) {
    const legend = document.getElementById("elevation-map-legend");
    if (!legend) return;
    legend.classList.toggle("elevation-map-legend-hidden", !visible);
}

// =====================================================
// NDVI: ЛЕГЕНДА НА КАРТЕ
// =====================================================
function ensureNdviLegend() {
    if (document.getElementById("ndvi-map-legend")) return;
    // Легенда добавляется в DOM один раз и просто показывается/скрывается при работе со слоями NDVI.
    const legend = document.createElement("div");
    legend.id = "ndvi-map-legend";
    legend.className = "ndvi-map-legend ndvi-map-legend-hidden";
    legend.innerHTML = `
        <div class="ndvi-map-legend-title">Легенда NDVI</div>
        <div class="ndvi-map-legend-bar"></div>
        <div class="ndvi-map-legend-labels">
            <span>0.0</span>
            <span>0.2</span>
            <span>0.4</span>
            <span>0.6</span>
            <span>0.8+</span>
        </div>
        <div class="ndvi-map-legend-hint">Красный: низкая вегетация, зеленый: высокая</div>
    `;
    document.body.appendChild(legend);
}

function setNdviLegendVisible(visible) {
    const legend = document.getElementById("ndvi-map-legend");
    if (!legend) return;
    legend.classList.toggle("ndvi-map-legend-hidden", !visible);
    layoutMapLegends();
}

// =====================================================
// ОТРИСОВКА ГРАФИКА ТРЕНДА (SVG)
// =====================================================
function updateNdviChart(labels, data) {
    const svgContainer = document.querySelector("#trend-chart-box svg");
    const pathEl = document.getElementById("ndvi-path-line");

    if (!svgContainer || !pathEl) return;

    // 1. Удаляем старые динамические точки (если есть)
    document.querySelectorAll('.dynamic-chart-node').forEach(el => el.remove());

    // 2. Прячем старые статические точки (node-1 ... node-5)
    for(let i=1; i<=5; i++) {
        const node = document.getElementById(`node-${i}`);
        if(node) node.style.display = "none";
    }

    // Если данных нет — очищаем линию
    if (!data || data.length === 0) {
        pathEl.setAttribute("d", "");
        renderNdviXAxisLabels(svgContainer, []);
        return;
    }

    // 3. Настройки геометрии графика
    const xMin = 30;
    const xMax = 310;
    const chartWidth = 320;  // Соответствует viewBox 0 0 320 160
    const chartHeight = 150; // Примерная высота SVG viewBox
    const paddingTop = 20;
    const paddingBottom = 20;

    // Функция перевода значения NDVI (0...1) в Y координату на экране (SVG)
    const scaleY = (val) => {
        // Защита от выхода за пределы
        const safeVal = Math.max(0, Math.min(1, val));
        const innerHeight = chartHeight - paddingTop - paddingBottom;
        // Y в SVG инвертирован (0 сверху, 150 снизу)
        return paddingTop + innerHeight - (safeVal * innerHeight);
    };

    // Шаг по оси X. Для одной точки ставим её по центру графика.
    const stepX = (xMax - xMin) / Math.max(1, (data.length - 1));

    // 4. Рисуем новую линию (d атрибут)
    let d = "";
    data.forEach((val, i) => {
        const x = data.length === 1 ? (xMin + xMax) / 2 : (xMin + (i * stepX));
        const y = scaleY(val);

        if (i === 0) {
            d += `M ${x},${y}`;
        } else {
            // Рисуем сглаженную кубическую кривую (C) или прямую линию (L)
            // Используем прямую для простоты и точности
            d += ` L ${x},${y}`;
        }

        // 5. Создаем точку (кружок) на графике
        const circle = document.createElementNS("http://www.w3.org/2000/svg", "circle");
        circle.setAttribute("cx", x);
        circle.setAttribute("cy", y);
        circle.setAttribute("r", 5);
        circle.setAttribute("fill", "#10b981"); // Изумрудный цвет
        circle.setAttribute("class", "dynamic-chart-node");
        circle.setAttribute("data-tip", `${labels[i]}: ${val.toFixed(2)}`); // Тултип
        circle.style.cursor = "pointer";
        circle.style.transition = "r 0.2s";

        // Эффект увеличения точки при наведении (дополнительно к тултипу)
        circle.addEventListener("mouseenter", () => circle.setAttribute("r", 7));
        circle.addEventListener("mouseleave", () => circle.setAttribute("r", 5));

        svgContainer.appendChild(circle);
    });

    // Применяем линию к SVG
    pathEl.setAttribute("d", d);
    renderNdviXAxisLabels(svgContainer, labels || []);
}

function renderNdviXAxisLabels(svgContainer, labels) {
    document.querySelectorAll(".dynamic-x-label").forEach(el => el.remove());
    // Убираем исходные статические подписи месяцев из шаблона.
    svgContainer.querySelectorAll('text[y="146"]:not(.dynamic-x-label)').forEach(el => el.remove());
    if (!labels || labels.length === 0) return;
    const xMin = 30;
    const xMax = 310;
    const stepX = (xMax - xMin) / Math.max(1, labels.length - 1);
    labels.forEach((label, i) => {
        const x = labels.length === 1 ? (xMin + xMax) / 2 : (xMin + i * stepX);
        const text = document.createElementNS("http://www.w3.org/2000/svg", "text");
        text.setAttribute("x", x);
        text.setAttribute("y", "146");
        text.setAttribute("font-size", "9");
        text.setAttribute("fill", "#94a3b8");
        text.setAttribute("text-anchor", "middle");
        text.setAttribute("class", "dynamic-x-label");
        text.textContent = label;
        svgContainer.appendChild(text);
    });
}

// =====================================================
// ДИНАМИЧЕСКОЕ РАСПРЕДЕЛЕНИЕ КУЛЬТУР ПО ВЫБРАННЫМ ПОЛЯМ
// =====================================================
let cropRecalcBound = false;

function initCropRecalculation() {
    if (cropRecalcBound) return;
    cropRecalcBound = true;
    document.getElementById("crop-fields-checklist")?.addEventListener("change", updateCropDistributionChart);
}

// =====================================================
// ДИНАМИЧЕСКИЙ ПЕРЕРАСЧЕТ ЗДОРОВЬЯ ПОЧВЫ НА БАЗЕ ПОЛЕЙ (UX/UI)
// =====================================================
function initSoilRecalculation() {
    document.getElementById("soil-fields-checklist")?.addEventListener("change", () => {
        const checkedCount = document.querySelectorAll(".soil-filter-checkbox:checked").length;
        const seed = checkedCount > 0 ? checkedCount : 3;

        // Расчет псевдослучайных флуктуаций значений почвы
        const moisture = Math.min(95, Math.max(25, 42 + (seed * 8) % 40));
        const organic = Math.min(98, Math.max(30, 58 + (seed * 7) % 35));
        const ph = Math.min(95, Math.max(40, 68 + (seed * 5) % 25));

        // Обновляем числа на панели
        const vals = document.querySelectorAll(".soil-val");
        if (vals[0]) vals[0].textContent = `${moisture}%`;
        if (vals[1]) vals[1].textContent = `${organic}%`;
        if (vals[2]) vals[2].textContent = `${ph}%`;

        // Обновляем прогресс-бары и тултипы
        const moistureBar = document.querySelector(".soil-moisture-bar");
        const organicBar = document.querySelector(".soil-organic-bar");
        const phBar = document.querySelector(".soil-ph-bar");

        if (moistureBar) {
            moistureBar.style.width = `${moisture}%`;
            moistureBar.setAttribute("data-tip", `Влажность: ${moisture}% (Анализ полей)`);
        }
        if (organicBar) {
            organicBar.style.width = `${organic}%`;
            organicBar.setAttribute("data-tip", `Органика: ${organic}% (Анализ полей)`);
        }
        if (phBar) {
            phBar.style.width = `${ph}%`;
            phBar.setAttribute("data-tip", `Активность pH: ${(ph/13).toFixed(1)} (${ph}% здоровья)`);
        }
    });
}

// =====================================================
// ДВИЖОК ТУЛТИПОВ ДЛЯ ГРАФИКОВ (Hover Values)
// =====================================================
function initChartTooltips() {
    const tooltipEl = document.getElementById("chart-tooltip");
    if (!tooltipEl) return;

    // Следование за курсором
    document.addEventListener("mousemove", (e) => {
        if (tooltipEl.style.display === "block") {
            tooltipEl.style.left = (e.clientX + 14) + "px";
            tooltipEl.style.top = (e.clientY + 14) + "px";
        }
    });

    // Делегирование событий наведения для любых тегов с атрибутом data-tip
    document.addEventListener("mouseover", (e) => {
        const target = e.target.closest("[data-tip]");
        if (target) {
            tooltipEl.textContent = target.getAttribute("data-tip");
            tooltipEl.style.display = "block";
        }
    });

    document.addEventListener("mouseout", (e) => {
        const target = e.target.closest("[data-tip]");
        if (target) {
            tooltipEl.style.display = "none";
        }
    });
}

function expandBounds(bounds, geometry) {
    if (!geometry || !geometry.coordinates) return;

    if (geometry.type === "Polygon") {
        geometry.coordinates[0].forEach(coord => bounds.extend(coord));
    } else if (geometry.type === "MultiPolygon") {
        geometry.coordinates.forEach(polygon => {
            polygon[0].forEach(coord => bounds.extend(coord));
        });
    }
}