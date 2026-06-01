/**
 * Импорт полей из ZIP-архива с shapefile.
 *
 * Поток:
 *   1. Пользователь загружает ZIP → POST /api/fields/import (единственное обращение,
 *      на бэкенде архив один раз уходит во внешний AI-сервис и возвращается GeoJSON).
 *   2. Поля показываются на карте как предпросмотр; аналитика заблокирована.
 *   3. Пользователь проверяет контуры и жмёт «Сохранить в базу» →
 *      POST /api/fields/import/commit (сохранение силами Java, без внешних сервисов).
 *
 * Зависит от глобалей из app.js: map, apiPath, fetchOpts, loadFields; и csrfHeaders из csrf.js.
 */
(function () {
    "use strict";

    const PREVIEW_SOURCE = "import-preview-source";
    const PREVIEW_FILL = "import-preview-fill";
    const PREVIEW_OUTLINE = "import-preview-outline";

    // GeoJSON, полученный от сервиса и ожидающий подтверждения пользователем.
    let pendingImport = null;
    let selectedFile = null;
    let previewHandlersBound = false;

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
                if (data && data.csrf && data.csrf.token) {
                    return {
                        headerName: data.csrf.headerName || "X-XSRF-TOKEN",
                        token: data.csrf.token
                    };
                }
            }
        } catch (e) {
            console.warn("loadCsrf:", e);
        }
        const cookieToken = typeof readXsrfToken === "function" ? readXsrfToken() : "";
        return { headerName: "X-XSRF-TOKEN", token: cookieToken };
    }

    async function csrfRequestHeaders(extraHeaders) {
        const headers = Object.assign({}, extraHeaders || {});
        const csrf = await loadCsrf();
        if (csrf.token) {
            headers[csrf.headerName] = csrf.token;
        }
        return headers;
    }

    // ---------------------------------------------------------------
    // Модалка выбора файла
    // ---------------------------------------------------------------
    function setupModal() {
        const modal = document.getElementById("import-modal");
        const dropzone = document.getElementById("dropzone");
        const fileInput = document.getElementById("file-input");
        const fileInfo = document.getElementById("import-file-info");
        const fileName = document.getElementById("selected-file-name");
        const statusText = document.getElementById("import-status-text");
        const btnSubmit = document.getElementById("btn-submit-import");

        const closeButtons = [
            document.getElementById("btn-close-import"),
            document.getElementById("btn-cancel-import")
        ];

        function resetModal() {
            selectedFile = null;
            if (fileInput) fileInput.value = "";
            if (fileInfo) fileInfo.style.display = "none";
            if (fileName) fileName.textContent = "—";
            if (statusText) statusText.textContent = "";
            btnSubmit?.setAttribute("disabled", "true");
        }

        function closeModal() {
            modal?.classList.remove("open");
            resetModal();
        }

        function selectFile(file) {
            if (!file) return;
            const isZip = /\.zip$/i.test(file.name)
                || file.type === "application/zip"
                || file.type === "application/x-zip-compressed";
            if (!isZip) {
                if (statusText) {
                    statusText.textContent = "Допустим только ZIP-архив с shapefile (.zip)";
                    statusText.classList.add("import-status-error");
                }
                return;
            }
            selectedFile = file;
            if (fileName) fileName.textContent = `${file.name} (${(file.size / 1024).toFixed(1)} Кб)`;
            if (fileInfo) fileInfo.style.display = "block";
            if (statusText) {
                statusText.textContent = "";
                statusText.classList.remove("import-status-error");
            }
            btnSubmit?.removeAttribute("disabled");
        }

        closeButtons.forEach(btn => btn?.addEventListener("click", closeModal));

        dropzone?.addEventListener("click", () => fileInput?.click());
        dropzone?.addEventListener("dragover", (e) => {
            e.preventDefault();
            dropzone.classList.add("dragover");
        });
        dropzone?.addEventListener("dragleave", () => dropzone.classList.remove("dragover"));
        dropzone?.addEventListener("drop", (e) => {
            e.preventDefault();
            dropzone.classList.remove("dragover");
            if (e.dataTransfer?.files?.length) selectFile(e.dataTransfer.files[0]);
        });

        fileInput?.addEventListener("change", (e) => {
            if (e.target.files?.length) selectFile(e.target.files[0]);
        });

        btnSubmit?.addEventListener("click", async () => {
            if (window.geoFieldIntakeActive) {
                alert("Сначала завершите или отмените добавление поля.");
                return;
            }
            if (!selectedFile) return;
            await submitImport(selectedFile, { modal, statusText, btnSubmit, closeModal });
        });
    }

    async function submitImport(file, ui) {
        const { statusText, btnSubmit, closeModal } = ui;
        btnSubmit?.setAttribute("disabled", "true");
        if (statusText) {
            statusText.classList.remove("import-status-error");
            statusText.innerHTML = '<span class="loading-inline"><span class="loading-spinner"></span>Распознавание полей...</span>';
        }

        try {
            const formData = new FormData();
            formData.append("file", file);

            // Для multipart НЕ задаём Content-Type вручную — браузер сам проставит boundary.
            const headers = await csrfRequestHeaders({});
            const response = await fetch(apiUrl("/api/fields/import"), Object.assign({}, baseFetchOpts(), {
                method: "POST",
                headers,
                body: formData
            }));

            if (!response.ok) {
                let detail = `HTTP ${response.status}`;
                try {
                    const err = await response.json();
                    detail = err.error || err.detail || detail;
                } catch (_) { /* ignore */ }
                throw new Error(detail);
            }

            const data = await response.json();
            const features = data?.fields?.features || [];
            if (!features.length) {
                throw new Error("Сервис не вернул ни одного поля");
            }

            pendingImport = data;
            closeModal?.();
            showPreview(data);
        } catch (error) {
            console.error("submitImport:", error);
            if (statusText) {
                statusText.classList.add("import-status-error");
                statusText.textContent = `Ошибка импорта: ${error.message}`;
            }
            btnSubmit?.removeAttribute("disabled");
        }
    }

    // ---------------------------------------------------------------
    // Предпросмотр на карте + блокировка аналитики
    // ---------------------------------------------------------------
    function previewFeatureCollection(data) {
        return {
            type: "FeatureCollection",
            features: (data?.fields?.features || []).map((f, idx) => ({
                type: "Feature",
                id: idx,
                properties: { idx: idx, name: f?.properties?.name || `Поле ${idx + 1}` },
                geometry: f.geometry
            }))
        };
    }

    function showPreview(data) {
        const m = getMap();
        const fc = previewFeatureCollection(data);

        if (m) {
            removePreviewLayers(m);
            m.addSource(PREVIEW_SOURCE, { type: "geojson", data: fc });
            m.addLayer({
                id: PREVIEW_FILL,
                type: "fill",
                source: PREVIEW_SOURCE,
                paint: { "fill-color": "#f59e0b", "fill-opacity": 0.25 }
            });
            m.addLayer({
                id: PREVIEW_OUTLINE,
                type: "line",
                source: PREVIEW_SOURCE,
                paint: { "line-color": "#d97706", "line-width": 2, "line-dasharray": [2, 1.5] }
            });
            bindPreviewInteractions(m);
            fitToPreview(m, fc);
        }

        setImportPreviewMode(true, fc.features.length);
        // История всегда на виду: открываем панель и показываем первое поле.
        openImportHistoryPanel();
        if (fc.features.length) {
            showImportHistory(0);
        }
    }

    // Клик по полю предпросмотра → обновляем панель истории этим полем.
    function bindPreviewInteractions(m) {
        if (previewHandlersBound) return;
        previewHandlersBound = true;
        m.on("click", PREVIEW_FILL, (e) => {
            const feature = e.features && e.features[0];
            if (!feature) return;
            const idx = feature.id != null ? feature.id : feature.properties?.idx;
            if (idx != null) showImportHistory(Number(idx));
        });
        m.on("mouseenter", PREVIEW_FILL, () => { m.getCanvas().style.cursor = "pointer"; });
        m.on("mouseleave", PREVIEW_FILL, () => { m.getCanvas().style.cursor = ""; });
    }

    function fitToPreview(m, fc) {
        try {
            const bounds = new maplibregl.LngLatBounds();
            let has = false;
            fc.features.forEach(feature => {
                eachCoord(feature.geometry, (lng, lat) => {
                    bounds.extend([lng, lat]);
                    has = true;
                });
            });
            if (has) m.fitBounds(bounds, { padding: 80, duration: 1000 });
        } catch (e) {
            console.warn("fitToPreview:", e);
        }
    }

    function eachCoord(geometry, cb) {
        if (!geometry) return;
        const walk = (coords) => {
            if (typeof coords[0] === "number") {
                cb(coords[0], coords[1]);
            } else {
                coords.forEach(walk);
            }
        };
        if (geometry.coordinates) walk(geometry.coordinates);
    }

    function removePreviewLayers(m) {
        [PREVIEW_FILL, PREVIEW_OUTLINE].forEach(id => {
            if (m.getLayer(id)) m.removeLayer(id);
        });
        if (m.getSource(PREVIEW_SOURCE)) m.removeSource(PREVIEW_SOURCE);
    }

    function clearPreview() {
        const m = getMap();
        if (m) removePreviewLayers(m);
        pendingImport = null;
        setImportPreviewMode(false, 0);
        if (typeof closeFieldHistoryPanel === "function") closeFieldHistoryPanel();
    }

    // ---------------------------------------------------------------
    // Панель истории импортируемых полей (та же, что у обычных полей)
    // ---------------------------------------------------------------
    function openImportHistoryPanel() {
        const panel = typeof ensureFieldHistoryPanel === "function"
            ? ensureFieldHistoryPanel()
            : document.getElementById("field-history-panel");
        if (!panel) return;
        const title = document.getElementById("field-history-title");
        const body = document.getElementById("field-history-body");
        if (title) title.textContent = "История импортируемых полей";
        if (body) {
            body.innerHTML = '<p class="muted font-sm m-0">Кликните на поле на карте, чтобы проверить его историю.</p>';
        }
        panel.classList.add("open");
        if (typeof syncFieldHistoryPanelLayout === "function") syncFieldHistoryPanelLayout();
    }

    function showImportHistory(idx) {
        const feature = pendingImport?.fields?.features?.[idx];
        if (!feature) return;
        const panel = typeof ensureFieldHistoryPanel === "function"
            ? ensureFieldHistoryPanel()
            : document.getElementById("field-history-panel");
        if (!panel) return;

        const name = feature.properties?.name || `Поле ${idx + 1}`;
        const title = document.getElementById("field-history-title");
        const body = document.getElementById("field-history-body");
        if (title) title.textContent = `История поля: ${name} (импорт)`;
        if (body) body.innerHTML = buildHistoryTable(feature.properties?.history);

        panel.classList.add("open");
        if (typeof syncFieldHistoryPanelLayout === "function") syncFieldHistoryPanelLayout();
    }

    function esc(value) {
        if (typeof escapeHtml === "function") return escapeHtml(value);
        if (value == null) return "";
        return String(value)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }

    function cell(value) {
        return (value === null || value === undefined || value === "") ? "—" : esc(value);
    }

    function buildHistoryTable(history) {
        const rows = Array.isArray(history) ? history : [];
        if (!rows.length) {
            return '<p class="muted font-sm m-0">История севооборота отсутствует.</p>';
        }
        const sorted = [...rows].sort((a, b) => (b.cropYear || 0) - (a.cropYear || 0));
        return `
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
                            <td>${cell(r.cropYear)}</td>
                            <td>${cell(r.cropName)}</td>
                            <td>${cell(r.sowingDate)}</td>
                            <td>${cell(r.harvestDate)}</td>
                            <td>${cell(r.sownAreaHa)}</td>
                            <td>${cell(r.actualYield)}</td>
                        </tr>
                    `).join("")}
                </tbody>
            </table>
        `;
    }

    /** Режим предпросмотра: показываем баннер и блокируем аналитику до сохранения. */
    function setImportPreviewMode(active, count) {
        window.geoImportPreviewActive = active;
        document.body.classList.toggle("import-preview-mode", active);
        document.body.classList.toggle("map-workflow-lock", active || window.geoFieldIntakeActive === true);

        const banner = document.getElementById("import-preview-banner");
        if (banner) {
            banner.classList.toggle("hidden", !active);
            const countEl = banner.querySelector("[data-import-count]");
            if (countEl) countEl.textContent = String(count);
        }

        // На время импорта запрещаем переходы: закрываем меню и боковые панели.
        if (active) {
            document.getElementById("sidebar")?.classList.remove("open");
            document.getElementById("analytics-panel")?.classList.remove("open");
            document.getElementById("layers-panel")?.classList.remove("open");
            document.getElementById("filter-panel")?.classList.remove("open");
        }

        // Доступность аналитики считается централизованно (нет полей / идёт импорт).
        if (typeof updateAnalyticsAvailability === "function") {
            updateAnalyticsAvailability();
        } else {
            const analyticsToggle = document.getElementById("analytics-toggle");
            analyticsToggle?.classList.toggle("is-disabled", active);
            if (analyticsToggle) analyticsToggle.toggleAttribute("disabled", active);
        }
    }

    // ---------------------------------------------------------------
    // Сохранение / отмена
    // ---------------------------------------------------------------
    function setupBanner() {
        document.getElementById("btn-import-save")?.addEventListener("click", saveImport);
        document.getElementById("btn-import-discard")?.addEventListener("click", () => {
            clearPreview();
        });
    }

    async function saveImport() {
        if (!pendingImport) return;
        const saveBtn = document.getElementById("btn-import-save");
        const discardBtn = document.getElementById("btn-import-discard");
        const originalLabel = saveBtn?.textContent;

        saveBtn?.setAttribute("disabled", "true");
        discardBtn?.setAttribute("disabled", "true");
        if (saveBtn) saveBtn.textContent = "Сохранение...";

        try {
            const headers = await csrfRequestHeaders({ "Content-Type": "application/json" });
            const response = await fetch(apiUrl("/api/fields/import/commit"), Object.assign({}, baseFetchOpts(), {
                method: "POST",
                headers,
                body: JSON.stringify(pendingImport)
            }));

            if (!response.ok) {
                let detail = `HTTP ${response.status}`;
                try {
                    const err = await response.json();
                    detail = err.error || err.detail || detail;
                } catch (_) { /* ignore */ }
                throw new Error(detail);
            }

            const result = await response.json();
            clearPreview();
            if (typeof loadFields === "function") {
                await loadFields();
            }
            alert(result.message || `Импортировано полей: ${result.savedFields}`);
        } catch (error) {
            console.error("saveImport:", error);
            alert(`Не удалось сохранить поля: ${error.message}`);
            saveBtn?.removeAttribute("disabled");
            discardBtn?.removeAttribute("disabled");
            if (saveBtn && originalLabel) saveBtn.textContent = originalLabel;
        }
    }

    // ---------------------------------------------------------------
    // Инициализация
    // ---------------------------------------------------------------
    function init() {
        setupModal();
        setupBanner();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
