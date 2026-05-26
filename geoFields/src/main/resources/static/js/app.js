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
const pythonFetchOpts = { // Отдельные опции для Python, если нужно
    credentials: _pythonApiBase ? "include" : "same-origin"
};

function apiPath(path) {
    const p = path.startsWith("/") ? path : "/" + path;
    return _apiBase ? _apiBase + p : p;
}
function pythonApiPath(path) { // Для Python API
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
    try { setupSidebarAndPanels(); } catch (e) { console.error("Сбой SidebarAndPanels:", e); }
    try { setupNdviCalculation(); } catch (e) { console.error("Сбой NdviCalculation:", e); }
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
// ЗАГРУЗКА И ОТОБРАЖЕНИЕ ПОЛЕЙ
// =====================================================
async function loadFields() {
    try {
        const response = await fetch(apiPath("/get_fields"), fetchOpts);
        if (!response.ok) throw new Error();

        const data = await response.json();
        loadedFieldsGeoJson = data;

        populateFilterFieldsList(data);
        populateNdviFieldsChecklist(data); 

        if (!map) return;

        if (map.getLayer("fields-fill")) map.removeLayer("fields-fill");
        if (map.getLayer("fields-outline")) map.removeLayer("fields-outline");
        if (map.getSource("fields-source")) map.removeSource("fields-source");

        map.addSource("fields-source", {
            type: "geojson",
            data: data,
            generateId: true
        });

        map.addLayer({
            id: "fields-fill",
            type: "fill",
            source: "fields-source",
            paint: {
                "fill-color": "#10b981",
                "fill-opacity": 0.22
            }
        });

        map.addLayer({
            id: "fields-outline",
            type: "line",
            source: "fields-source",
            paint: {
                "line-color": "#10b981",
                "line-width": 2
            }
        });

        // Навешиваем клик на полигоны полей для показа сводки и истории сева (Новое!)
        map.on("click", "fields-fill", (e) => {
            if (isMeasuring || !e.features || e.features.length === 0) return;

            const properties = e.features[0].properties;
            const name = properties.name || "Без названия";
            const area = properties.area ? Number(properties.area).toFixed(1) : "Н/Д";

            // Безопасно разбираем динамическую историю поля (так как MapLibre сжимает массивы в JSON-строки)
            let historyRecords = [];
            if (properties.history) {
                if (typeof properties.history === "string") {
                    try {
                        historyRecords = JSON.parse(properties.history);
                    } catch (err) {
                        historyRecords = [];
                    }
                } else if (Array.isArray(properties.history)) {
                    historyRecords = properties.history;
                }
            }

            // Формируем блок с последней актуальной записью севооборота
            let latestRecordHtml = "";
            if (historyRecords && historyRecords.length > 0) {
                // Сортируем записи по году (самый свежий сезон вверху)
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

            // Выводим попап на карте
            new maplibregl.Popup()
                .setLngLat(e.lngLat)
                .setHTML(`
                    <div style="font-family:'Inter', sans-serif; padding:4px; min-width:180px;">
                        <strong style="display:block; margin-bottom:4px; color:#10b981; font-size:13px;">🌿 ${name}</strong>
                        <span style="font-size:12px; color:#64748b; font-weight:500;">Площадь: ${area} га</span>
                        ${latestRecordHtml}
                    </div>
                `)
                .addTo(map);
        });

        // Смена курсора
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

    const fillContainer = (containerEl, cssClass) => {
        if (!containerEl) return;
        containerEl.innerHTML = "";
        geojson.features.forEach(feature => {
            const props = feature.properties || {};
            const id = props.id || props.fieldId || feature.id;
            const name = props.name || props.fieldName || `Поле #${id}`;

            const label = document.createElement("label");
            label.className = "filter-checkbox-row";
            label.innerHTML = `
                <input type="checkbox" value="${id}" class="${cssClass}" checked="checked" />
                <span>${name}</span>
            `;
            containerEl.appendChild(label);
        });
    };

    fillContainer(ndviBox, "ndvi-filter-checkbox");
    fillContainer(cropBox, "crop-filter-checkbox");
    fillContainer(soilBox, "soil-filter-checkbox");

    initCropRecalculation();
    initSoilRecalculation();
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
function setupFilterControls() {
    const btnApply = document.getElementById("btn-apply-filters");
    const btnReset = document.getElementById("btn-reset-filters");

    btnApply?.addEventListener("click", () => {
        const minArea = parseFloat(document.getElementById("filter-area-min")?.value) || 0;
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
            const history = props.history || [];
            const ndviVal = props.ndvi_mean || 0.65; 

            let fieldMatch = true;
            if (selectedFieldIds.length > 0) {
                fieldMatch = selectedFieldIds.includes(String(id));
            }

            const areaOk = area >= minArea;

            let cropOk = true;
            if (cropType !== "all") {
                cropOk = history.some(record => record.cropName === cropType);
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
    });

    btnReset?.addEventListener("click", () => {
        const areaMinEl = document.getElementById("filter-area-min");
        const cropTypeEl = document.getElementById("filter-crop-type");
        const ndviMinEl = document.getElementById("filter-ndvi-min");
        const ndviMaxEl = document.getElementById("filter-ndvi-max");
        const statusLevelEl = document.getElementById("filter-status-level");

        if (areaMinEl) areaMinEl.value = "";
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
    });
}

// =====================================================
// ИМПОРТ ДАННЫХ
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
function setupSidebarAndPanels() {
    document.getElementById("sidebar-toggle")?.addEventListener("click", () => {
        document.getElementById("sidebar")?.classList.toggle("open");
    });

    document.getElementById("analytics-toggle")?.addEventListener("click", () => {
        document.getElementById("analytics-panel")?.classList.add("open");
    });

    document.getElementById("analytics-close")?.addEventListener("click", () => {
        document.getElementById("analytics-panel")?.classList.remove("open");
    });

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
}

// =====================================================
// ПЕРЕКЛЮЧЕНИЕ ТАБОВ АНАЛИТИКИ (ВЕГЕТАЦИЯ / ПОГОДА)
// =====================================================
function setupAnalyticsTabs() {
    const tabs = document.querySelectorAll(".analytics-tab");
    tabs.forEach(tab => {
        tab.addEventListener("click", () => {
            tabs.forEach(t => t.classList.remove("active"));
            tab.classList.add("active");

            const activeTabName = tab.getAttribute("data-tab");
            const vegContent = document.getElementById("tab-content-veg");
            const weatherContent = document.getElementById("tab-content-weather");

            if (activeTabName === "veg") {
                vegContent?.classList.remove("tab-content-hidden");
                weatherContent?.classList.add("tab-content-hidden");
            } else if (activeTabName === "weather") {
                vegContent?.classList.add("tab-content-hidden");
                weatherContent?.classList.remove("tab-content-hidden");
            }
        });
    });
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
        const endDate = endDateInput?.value;

        if (selectedFieldIds.length === 0) {
            alert("Пожалуйста, сначала выберите поля галочками.");
            return;
        }
        if (!startDate) {
            alert("Пожалуйста, выберите 'Дату начала'.");
            return;
        }

        const trendTitle = document.getElementById("trend-title-ref");
        if (trendTitle) trendTitle.textContent = "⌛ Загрузка снимков...";

        // 1. ПОЛНОСТЬЮ ОЧИЩАЕМ КАРТУ И МЕНЮ ОТ СТАРЫХ РАСТРОВ
        clearNdviLayers();

        try {
            // 2. ЗАГРУЖАЕМ РАСТРЫ ДЛЯ КАЖДОГО ВЫБРАННОГО ПОЛЯ
            const tilePromises = selectedFieldIds.map(fieldId => {
                return fetch(pythonApiPath(`/get_ndvi_tiles_for_field?field_id=${fieldId}&date=${startDate}`), pythonFetchOpts)
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
                    addNdviLayer(result.data.url, layerId);

                    // Добавляем переключатель в панель слоев
                    addLayerToggleToMenu(layerId, result.fieldId, result.data.actual_date);

                    activeNdviLayerIds.push(layerId);
                    loadedCount++;
                }
            });

            if (trendTitle) trendTitle.textContent = `Отображено растров: ${loadedCount} из ${selectedFieldIds.length}`;

            // 4. ГРАФИК ТРЕНДА (Вторичная задача)
            try {
                if (endDate) {
                    const trendResponse = await fetch(pythonApiPath(`/get_ndvi_trend?${selectedFieldIds.map(id => `field_id=${id}`).join('&')}&start_date=${startDate}&end_date=${endDate}`), pythonFetchOpts);
                    if (trendResponse.ok) {
                        const trendData = await trendResponse.json();
                        updateNdviChart(trendData.labels, trendData.data);
                        const avgNdviEl = document.getElementById("calc-avg-ndvi");
                        if (avgNdviEl && trendData.data.length > 0) {
                            avgNdviEl.textContent = (trendData.data.reduce((a, b) => a + b, 0) / trendData.data.length).toFixed(2);
                        }
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

// Функция добавления слоя на карту
function addNdviLayer(tileUrlTemplate, layerId) {
    if (!map) return;
    if (map.getLayer(layerId)) {
        map.removeLayer(layerId);
        if (map.getSource(layerId)) map.removeSource(layerId);
    }
    map.addSource(layerId, { type: "raster", tiles: [tileUrlTemplate], tileSize: 256 });
    map.addLayer({
        id: layerId, type: "raster", source: layerId,
        paint: { "raster-opacity": 0.8 }
    }, "fields-outline");
}

// НОВАЯ ФУНКЦИЯ: Добавляет чекбокс в панель слоев
function addLayerToggleToMenu(layerId, fieldId, dateStr) {
    const layersPanel = document.getElementById("layers-panel");
    if (!layersPanel) return;

    // Ищем или создаем контейнер специально для динамических слоев внутри панели
    let dynamicContainer = document.getElementById("dynamic-layers-container");
    if (!dynamicContainer) {
        dynamicContainer = document.createElement("div");
        dynamicContainer.id = "dynamic-layers-container";
        dynamicContainer.style.marginTop = "15px";
        dynamicContainer.style.borderTop = "1px solid #e2e8f0";
        dynamicContainer.style.paddingTop = "10px";
        layersPanel.appendChild(dynamicContainer);
    }

    // Создаем элемент списка (чекбокс + текст)
    const labelRow = document.createElement("label");
    labelRow.className = "filter-checkbox-row dynamic-ndvi-toggle"; // Используем ваши классы стилей
    labelRow.id = `toggle-${layerId}`;
    labelRow.style.display = "flex";
    labelRow.style.alignItems = "center";
    labelRow.style.marginBottom = "8px";
    labelRow.style.cursor = "pointer";

    // Сам чекбокс
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.checked = true; // Слой включен по умолчанию
    checkbox.style.marginRight = "8px";

    // Название слоя
    const span = document.createElement("span");
    span.textContent = `NDVI Поле #${fieldId} (${dateStr})`;
    span.style.fontSize = "13px";

    // Вешаем событие: при клике включаем/выключаем слой на карте
    checkbox.addEventListener("change", (e) => {
        if (map && map.getLayer(layerId)) {
            const visibility = e.target.checked ? 'visible' : 'none';
            map.setLayoutProperty(layerId, 'visibility', visibility);
        }
    });

    labelRow.appendChild(checkbox);
    labelRow.appendChild(span);
    dynamicContainer.appendChild(labelRow);
}

// НОВАЯ ФУНКЦИЯ: Очищает слои с карты и удаляет их из меню
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
    activeNdviLayerIds = []; // Обнуляем список
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
        return;
    }

    // 3. Настройки геометрии графика
    const chartWidth = 350;  // Примерная ширина SVG viewBox
    const chartHeight = 150; // Примерная высота SVG viewBox
    const paddingTop = 20;
    const paddingBottom = 20;
    const paddingLeft = 30;
    const paddingRight = 30;

    // Функция перевода значения NDVI (0...1) в Y координату на экране (SVG)
    const scaleY = (val) => {
        // Защита от выхода за пределы
        const safeVal = Math.max(0, Math.min(1, val));
        const innerHeight = chartHeight - paddingTop - paddingBottom;
        // Y в SVG инвертирован (0 сверху, 150 снизу)
        return paddingTop + innerHeight - (safeVal * innerHeight);
    };

    // Шаг по оси X
    const stepX = (chartWidth - paddingLeft - paddingRight) / Math.max(1, (data.length - 1));

    // 4. Рисуем новую линию (d атрибут)
    let d = "";
    data.forEach((val, i) => {
        const x = paddingLeft + (i * stepX);
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
}

// =====================================================
// ДИНАМИЧЕСКИЙ ПЕРЕРАСЧЕТ КУЛЬТУР НА БАЗЕ ПОЛЕЙ (UX/UI)
// =====================================================
function initCropRecalculation() {
    document.getElementById("crop-fields-checklist")?.addEventListener("change", () => {
        const checkedCount = document.querySelectorAll(".crop-filter-checkbox:checked").length;
        const baseFactor = checkedCount > 0 ? checkedCount : 1;

        // Эмулируем распределение культур в зависимости от набора полей
        const psh = (138.2 * (baseFactor * 0.4 + 0.6)).toFixed(1);
        const kuk = (189.3 * (1.5 / (baseFactor * 0.5 + 0.5))).toFixed(1);
        const soya = (152.4 * (baseFactor * 0.3 + 0.7)).toFixed(1);

        // Обновляем легенду
        const vals = document.querySelectorAll(".crop-val");
        if (vals[0]) vals[0].textContent = `${psh} га`;
        if (vals[1]) vals[1].textContent = `${kuk} га`;
        if (vals[2]) vals[2].textContent = `${soya} га`;

        // Обновляем спектральные срезы conic-gradient у пончика
        const total = parseFloat(psh) + parseFloat(kuk) + parseFloat(soya);
        const pshPct = Math.round((psh / total) * 100);
        const kukPct = Math.round((kuk / total) * 100);
        
        const donut = document.querySelector(".donut-chart");
        if (donut) {
            donut.style.background = `conic-gradient(#10b981 0% ${pshPct}%, #f59e0b ${pshPct}% ${pshPct + kukPct}%, #f97316 ${pshPct + kukPct}% 100%)`;
            donut.setAttribute("data-tip", `Пшеница: ${psh} га | Кукуруза: ${kuk} га | Соя: ${soya} га`);
        }
    });
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