/**
 * Ручное добавление одного поля: контур по точкам на карте + POST /api/org/agronomist/fields/intake.
 * На время режима блокируются аналитика и навигация (как при ИИ-импорте).
 */
(function () {
    "use strict";

    const SOURCE = "field-intake-source";
    const LAYER_FILL = "field-intake-fill";
    const LAYER_LINE = "field-intake-line";
    const LAYER_POINTS = "field-intake-points";

    let points = [];
    let crops = [];
    let mapClickBound = false;

    function getMap() {
        return typeof map !== "undefined" ? map : (window.map || null);
    }

    function apiUrl(path) {
        return typeof apiPath === "function" ? apiPath(path) : path;
    }

    function baseFetchOpts() {
        return typeof fetchOpts !== "undefined" ? fetchOpts : { credentials: "include" };
    }

    async function loadCsrf() {
        try {
            const res = await fetch(apiUrl("/api/session/context"), baseFetchOpts());
            if (res.ok) {
                const data = await res.json();
                if (data?.csrf?.token) {
                    return { headerName: data.csrf.headerName || "X-XSRF-TOKEN", token: data.csrf.token };
                }
            }
        } catch (e) {
            console.warn("field-intake loadCsrf:", e);
        }
        const cookieToken = typeof readXsrfToken === "function" ? readXsrfToken() : "";
        return { headerName: "X-XSRF-TOKEN", token: cookieToken };
    }

    async function csrfRequestHeaders(extra) {
        const headers = Object.assign({}, extra || {});
        const csrf = await loadCsrf();
        if (csrf.token) headers[csrf.headerName] = csrf.token;
        return headers;
    }

    function esc(value) {
        if (typeof escapeHtml === "function") return escapeHtml(value);
        if (value == null) return "";
        return String(value)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }

    function isOtherWorkflowActive() {
        return window.geoImportPreviewActive === true;
    }

    function setIntakeMode(active) {
        window.geoFieldIntakeActive = active;
        document.body.classList.toggle("field-intake-mode", active);
        document.body.classList.toggle("map-workflow-lock", active || window.geoImportPreviewActive === true);

        const banner = document.getElementById("field-intake-banner");
        banner?.classList.toggle("hidden", !active);

        const panel = ensureIntakePanel();
        panel?.classList.toggle("open", active);

        if (active) {
            document.getElementById("sidebar")?.classList.remove("open");
            document.getElementById("analytics-panel")?.classList.remove("open");
            document.getElementById("layers-panel")?.classList.remove("open");
            document.getElementById("filter-panel")?.classList.remove("open");
            if (typeof closeFieldHistoryPanel === "function") closeFieldHistoryPanel();
            if (typeof hideFieldContextMenu === "function") hideFieldContextMenu();
            if (typeof deactivateMeasure === "function") deactivateMeasure();
            document.getElementById("btn-measure")?.toggleAttribute("disabled", true);
            const m = getMap();
            if (m) m.getCanvas().style.cursor = "crosshair";
        } else {
            document.getElementById("btn-measure")?.removeAttribute("disabled");
            const m = getMap();
            if (m) m.getCanvas().style.cursor = "";
            removeMapLayers();
            points = [];
            renderPointsList();
            updatePreviewLayers();
        }

        if (typeof updateAnalyticsAvailability === "function") {
            updateAnalyticsAvailability();
        }
    }

    function ensureIntakePanel() {
        let panel = document.getElementById("field-intake-panel");
        if (panel) return panel;

        panel = document.createElement("aside");
        panel.id = "field-intake-panel";
        panel.className = "field-intake-panel";
        panel.innerHTML = `
            <div class="field-intake-panel-header">
                <h3>Контур поля</h3>
                <p class="field-intake-panel-hint">Клик по карте добавляет вершину. Минимум 3 точки для полигона.</p>
            </div>
            <div class="field-intake-panel-body">
                <div class="field-intake-form-row">
                    <label class="field-intake-label" for="intake-field-name">Название поля</label>
                    <input type="text" id="intake-field-name" class="field-intake-input" maxlength="200" placeholder="Например, Северное-1"/>
                </div>
                <div class="field-intake-form-row field-intake-form-row--split">
                    <div>
                        <label class="field-intake-label" for="intake-crop">Культура (запись истории)</label>
                        <select id="intake-crop" class="field-intake-input"></select>
                    </div>
                    <div>
                        <label class="field-intake-label" for="intake-crop-year">Год</label>
                        <input type="number" id="intake-crop-year" class="field-intake-input" min="1900" max="2100" step="1"/>
                    </div>
                </div>
                <div class="field-intake-coord-box">
                    <div class="field-intake-label">Точка по координатам</div>
                    <div class="field-intake-coord-inputs">
                        <input type="number" id="intake-lng" class="field-intake-input" step="any" placeholder="Долгота"/>
                        <input type="number" id="intake-lat" class="field-intake-input" step="any" placeholder="Широта"/>
                        <button type="button" class="filter-btn secondary" id="btn-intake-add-coord">Добавить</button>
                    </div>
                </div>
                <div class="field-intake-points-head">
                    <span class="field-intake-label">Вершины (<span id="intake-points-count">0</span>)</span>
                </div>
                <ul id="intake-points-list" class="field-intake-points-list"></ul>
            </div>
        `;
        document.body.appendChild(panel);

        document.getElementById("btn-intake-add-coord")?.addEventListener("click", addCoordFromInputs);
        document.getElementById("intake-lng")?.addEventListener("keydown", (e) => {
            if (e.key === "Enter") addCoordFromInputs();
        });
        document.getElementById("intake-lat")?.addEventListener("keydown", (e) => {
            if (e.key === "Enter") addCoordFromInputs();
        });

        return panel;
    }

    function ensureBanner() {
        if (document.getElementById("field-intake-banner")) return;
        const banner = document.createElement("div");
        banner.id = "field-intake-banner";
        banner.className = "field-intake-banner hidden";
        banner.innerHTML = `
            <div class="field-intake-banner-text">
                <strong>Добавление поля</strong>
                <span>Отметьте контур на карте или введите координаты в панели справа.</span>
            </div>
            <div class="field-intake-banner-actions">
                <button type="button" class="filter-btn primary" id="btn-intake-save">Сохранить поле</button>
                <button type="button" class="filter-btn secondary" id="btn-intake-cancel">Отменить</button>
            </div>
        `;
        document.body.appendChild(banner);
        document.getElementById("btn-intake-save")?.addEventListener("click", saveIntake);
        document.getElementById("btn-intake-cancel")?.addEventListener("click", cancelIntake);
    }

    function parseCoordInput(raw) {
        const n = Number(String(raw ?? "").trim().replace(",", "."));
        return Number.isFinite(n) ? n : null;
    }

    function addPoint(lng, lat) {
        if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
            alert("Укажите корректные координаты (долгота и широта).");
            return;
        }
        if (lng < -180 || lng > 180 || lat < -90 || lat > 90) {
            alert("Координаты вне допустимого диапазона.");
            return;
        }
        points.push([lng, lat]);
        renderPointsList();
        updatePreviewLayers();
    }

    function addCoordFromInputs() {
        const lng = parseCoordInput(document.getElementById("intake-lng")?.value);
        const lat = parseCoordInput(document.getElementById("intake-lat")?.value);
        if (lng == null || lat == null) {
            alert("Введите долготу и широту.");
            return;
        }
        addPoint(lng, lat);
        const lngEl = document.getElementById("intake-lng");
        const latEl = document.getElementById("intake-lat");
        if (lngEl) lngEl.value = "";
        if (latEl) latEl.value = "";
    }

    function removePoint(index) {
        if (index < 0 || index >= points.length) return;
        points.splice(index, 1);
        renderPointsList();
        updatePreviewLayers();
    }

    function renderPointsList() {
        const list = document.getElementById("intake-points-list");
        const countEl = document.getElementById("intake-points-count");
        if (countEl) countEl.textContent = String(points.length);
        if (!list) return;

        if (!points.length) {
            list.innerHTML = '<li class="field-intake-points-empty">Точек пока нет — кликните по карте или добавьте координаты.</li>';
            return;
        }

        list.innerHTML = points.map((pt, idx) => `
            <li class="field-intake-point-item">
                <span class="field-intake-point-index">${idx + 1}</span>
                <span class="field-intake-point-coords">${pt[0].toFixed(6)}, ${pt[1].toFixed(6)}</span>
                <button type="button" class="field-intake-point-remove" data-index="${idx}" title="Удалить">×</button>
            </li>
        `).join("");

        list.querySelectorAll(".field-intake-point-remove").forEach(btn => {
            btn.addEventListener("click", () => removePoint(parseInt(btn.getAttribute("data-index"), 10)));
        });
    }

    function buildFeatureCollection() {
        const features = points.map((pt, idx) => ({
            type: "Feature",
            properties: { idx },
            geometry: { type: "Point", coordinates: pt }
        }));

        if (points.length >= 2) {
            features.push({
                type: "Feature",
                properties: {},
                geometry: { type: "LineString", coordinates: points }
            });
        }

        if (points.length >= 3) {
            const ring = [...points, points[0]];
            features.push({
                type: "Feature",
                properties: {},
                geometry: { type: "Polygon", coordinates: [ring] }
            });
        }

        return { type: "FeatureCollection", features };
    }

    function ensureMapLayers(m) {
        if (!m.getSource(SOURCE)) {
            m.addSource(SOURCE, { type: "geojson", data: { type: "FeatureCollection", features: [] } });
        }
        if (!m.getLayer(LAYER_FILL)) {
            m.addLayer({
                id: LAYER_FILL,
                type: "fill",
                source: SOURCE,
                filter: ["==", "$type", "Polygon"],
                paint: { "fill-color": "#6366f1", "fill-opacity": 0.22 }
            });
        }
        if (!m.getLayer(LAYER_LINE)) {
            m.addLayer({
                id: LAYER_LINE,
                type: "line",
                source: SOURCE,
                filter: ["==", "$type", "LineString"],
                paint: { "line-color": "#4f46e5", "line-width": 2, "line-dasharray": [2, 1.5] }
            });
        }
        if (!m.getLayer(LAYER_POINTS)) {
            m.addLayer({
                id: LAYER_POINTS,
                type: "circle",
                source: SOURCE,
                filter: ["==", "$type", "Point"],
                paint: {
                    "circle-radius": 6,
                    "circle-color": "#4f46e5",
                    "circle-stroke-width": 2,
                    "circle-stroke-color": "#fff"
                }
            });
        }
    }

    function updatePreviewLayers() {
        const m = getMap();
        if (!m) return;
        ensureMapLayers(m);
        const src = m.getSource(SOURCE);
        if (src) src.setData(buildFeatureCollection());
    }

    function removeMapLayers() {
        const m = getMap();
        if (!m) return;
        [LAYER_POINTS, LAYER_LINE, LAYER_FILL].forEach(id => {
            if (m.getLayer(id)) m.removeLayer(id);
        });
        if (m.getSource(SOURCE)) m.removeSource(SOURCE);
    }

    function bindMapClick() {
        const m = getMap();
        if (!m || mapClickBound) return;
        mapClickBound = true;
        m.on("click", onMapClickAddPoint);
    }

    function onMapClickAddPoint(e) {
        if (!window.geoFieldIntakeActive) return;
        if (typeof isMeasuring !== "undefined" && isMeasuring) return;
        addPoint(e.lngLat.lng, e.lngLat.lat);
    }

    function buildPolygonGeometry() {
        if (points.length < 3) return null;
        const ring = points.map(pt => [pt[0], pt[1]]);
        ring.push([points[0][0], points[0][1]]);
        return { type: "Polygon", coordinates: [ring] };
    }

    async function loadCrops() {
        const res = await fetch(apiUrl("/api/org/agronomist/summary"), baseFetchOpts());
        if (!res.ok) throw new Error("Не удалось загрузить справочник культур");
        const data = await res.json();
        crops = data.crops || [];
        const select = document.getElementById("intake-crop");
        if (!select) return;
        select.innerHTML = crops.map(c =>
            `<option value="${c.cropId}">${esc(c.cropName)}</option>`
        ).join("");
    }

    function resetForm() {
        const nameEl = document.getElementById("intake-field-name");
        const yearEl = document.getElementById("intake-crop-year");
        if (nameEl) nameEl.value = "";
        if (yearEl) yearEl.value = String(new Date().getFullYear());
        points = [];
        renderPointsList();
        updatePreviewLayers();
    }

    async function startIntake() {
        if (isOtherWorkflowActive()) {
            alert("Сначала завершите или отмените импорт полей.");
            return;
        }
        if (window.geoFieldIntakeActive) return;
        if (!getMap()) {
            alert("Карта ещё не загружена. Подождите и попробуйте снова.");
            return;
        }

        try {
            ensureBanner();
            ensureIntakePanel();
            await loadCrops();
            resetForm();
            bindMapClick();
            setIntakeMode(true);
        } catch (err) {
            console.error("startIntake:", err);
            alert(err.message || "Не удалось начать добавление поля.");
        }
    }

    function cancelIntake() {
        setIntakeMode(false);
    }

    async function saveIntake() {
        const fieldName = document.getElementById("intake-field-name")?.value?.trim();
        const cropId = parseInt(document.getElementById("intake-crop")?.value, 10);
        const cropYear = parseInt(document.getElementById("intake-crop-year")?.value, 10);
        const geometry = buildPolygonGeometry();

        if (!fieldName) {
            alert("Укажите название поля.");
            return;
        }
        if (!geometry) {
            alert("Добавьте минимум 3 точки для контура.");
            return;
        }
        if (!Number.isFinite(cropId)) {
            alert("Выберите культуру для первой записи истории.");
            return;
        }
        if (!Number.isFinite(cropYear)) {
            alert("Укажите год для записи истории.");
            return;
        }

        const saveBtn = document.getElementById("btn-intake-save");
        const cancelBtn = document.getElementById("btn-intake-cancel");
        const original = saveBtn?.textContent;
        saveBtn?.setAttribute("disabled", "true");
        cancelBtn?.setAttribute("disabled", "true");
        if (saveBtn) saveBtn.textContent = "Сохранение…";

        const payload = {
            fieldName,
            geometry,
            history: {
                cropId,
                cropYear,
                sowingDate: null,
                harvestDate: null,
                sownAreaHa: null,
                harvestAreaHa: null,
                actualYield: null,
                totalYield: null,
                plannedYield: null,
                forecastedYield: null,
                sourceData: "manual_intake",
                sowingDetails: null
            }
        };

        try {
            const headers = await csrfRequestHeaders({ "Content-Type": "application/json" });
            const res = await fetch(apiUrl("/api/org/agronomist/fields/intake"), Object.assign({}, baseFetchOpts(), {
                method: "POST",
                headers,
                body: JSON.stringify(payload)
            }));
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                throw new Error(data.message || data.detail || `HTTP ${res.status}`);
            }
            setIntakeMode(false);
            if (typeof loadFields === "function") await loadFields();
            alert(data.message || "Поле создано.");
        } catch (err) {
            console.error("saveIntake:", err);
            alert(`Не удалось сохранить поле: ${err.message}`);
        } finally {
            saveBtn?.removeAttribute("disabled");
            cancelBtn?.removeAttribute("disabled");
            if (saveBtn && original) saveBtn.textContent = original;
        }
    }

    function init() {
        ensureBanner();
        document.addEventListener("click", (e) => {
            if (e.target.closest("#btn-add-field-trigger")) {
                e.preventDefault();
                startIntake();
            }
        });
        window.addEventListener("keydown", (e) => {
            if (e.key === "Escape" && window.geoFieldIntakeActive) cancelIntake();
        });
    }

    window.startFieldIntake = startIntake;
    window.cancelFieldIntake = cancelIntake;

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
