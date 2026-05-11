/**
 * Главная страница с картой (MapLibre): поля организации, NDVI, боковая панель пользователя.
 * Запросы к своему же origin с cookie сессии — бэкенд знает организацию и пользователя.
 */

// const API_URL = "";
const API_URL = "http://localhost:8080"; // раскомментируй, если открываешь HTML файлом (file://)

const _apiBase = String(API_URL || "").trim().replace(/\/$/, "");

const fetchOpts = { credentials: _apiBase ? "include" : "same-origin" };

function apiPath(path) {
    const p = path.startsWith("/") ? path : "/" + path;
    return _apiBase ? _apiBase + p : p;
}

let loadedFieldsGeoJson = null;
let loadedIntersectingGeoJson = { type: 'FeatureCollection', features: [] };
let activePopup = null;
const popupFieldCache = new Map();

let selectedFieldDbId = null;
let canOpenAgronomistPage = false;
let selectedMaplibreId = null;

const INTERSECTIONS_SOURCE_ID = 'intersections-source';
const INTERSECTIONS_FILL_ID = 'intersections-fill';
const INTERSECTIONS_OUTLINE_ID = 'intersections-outline';

/**
 * Подтягивает данные текущего пользователя с API (HTML главной без Thymeleaf).
 * Заполняет блок меню, ссылки менеджера/админа, скрытое поле CSRF для POST /logout.
 */
async function loadSessionContext() {
    const res = await fetch(apiPath("/api/session/context"), fetchOpts);
    if (!res.ok) {
        if (res.status === 401) {
            window.location.href = apiPath("/login");
        }
        return;
    }
    const d = await res.json();
    const el = (id) => document.getElementById(id);
    el("user-fullname").textContent = d.fullName || d.login || "—";
    el("user-role-label").textContent = d.roleLabel || "—";
    el("user-last-name").textContent = d.lastName != null ? d.lastName : "—";
    el("user-first-name").textContent = d.firstName != null ? d.firstName : "—";
    el("user-middle-name").textContent = d.middleName != null ? d.middleName : "—";
    el("user-login").textContent = d.login || "—";
    el("user-id").textContent = String(d.userId ?? "—");
    el("user-org-name").textContent = d.organizationName && d.organizationName.trim() !== ""
        ? d.organizationName
        : ("#" + (d.organizationId ?? "—"));

    if (d.csrf && d.csrf.parameterName && d.csrf.token) {
        const input = el("logout-csrf-param");
        input.name = d.csrf.parameterName;
        input.value = d.csrf.token;
    }
    el("user-panel").style.display = "block";
    el("user-logout-wrap").style.display = "block";
    if (d.orgManager || d.admin) {
        el("link-org-manager").style.display = "block";
    }
    if (d.admin) {
        el("link-org-admin").style.display = "block";
    }
    if (d.agronomist || d.admin) {
        el("link-org-agronomist").style.display = "block";
    }
    canOpenAgronomistPage = !!(d.agronomist || d.admin);
    updateAgronomistHistoryLink();
}

/** URL страницы истории посевов; если на карте выбрано поле — добавляется ?fieldId=… */
function agronomistHistoryPageUrl() {
    const base = apiPath("/org/agronomist");
    if (selectedFieldDbId == null || selectedFieldDbId === "") {
        return base;
    }
    const sep = base.includes("?") ? "&" : "?";
    return base + sep + "fieldId=" + encodeURIComponent(String(selectedFieldDbId));
}

/** Блок в боковой панели: какое поле сейчас выбрано на карте. */
function updateSelectedFieldInfo(props) {
    const span = document.getElementById("selected-field-info");
    if (!span) {
        return;
    }
    if (props == null || selectedFieldDbId == null || selectedFieldDbId === "") {
        span.textContent = "не выбрано";
        return;
    }
    const name = props.name != null && String(props.name).trim() !== "" ? String(props.name).trim() : "Без имени";
    span.textContent = name + " · id " + props.id;
}

function updateAgronomistHistoryLink() {
    const a = document.getElementById("link-org-agronomist-anchor");
    const hint = document.getElementById("link-org-agronomist-hint");
    if (!a) {
        return;
    }
    a.href = agronomistHistoryPageUrl();
    if (!hint) {
        return;
    }
    if (!canOpenAgronomistPage) {
        hint.style.display = "none";
        return;
    }
    if (selectedFieldDbId != null && selectedFieldDbId !== "") {
        hint.style.display = "none";
        hint.textContent = "";
    } else {
        hint.style.display = "block";
        hint.textContent = "Поле не выбрано.";
    }
}

// Карта: два растровых подложки переключаются чекбоксами в панели слоёв.
const map = new maplibregl.Map({
    container: 'map',
    style: {
        version: 8,
        sources: {
            'google-satellite': {
                type: 'raster',
                tiles: ['https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}'],
                tileSize: 256
            },
            'osm': {
                type: 'raster',
                tiles: ['https://a.tile.openstreetmap.org/{z}/{x}/{y}.png'],
                tileSize: 256,
                attribution: '© OpenStreetMap'
            }
        },
        layers: [
            {
                id: 'osm',
                type: 'raster',
                source: 'osm',
                layout: { 'visibility': 'none' }
            },
            {
                id: 'google-satellite',
                type: 'raster',
                source: 'google-satellite',
                layout: { 'visibility': 'visible' }
            }
        ]
    },
    center: [38.970, 45.035],
    zoom: 12
});

map.addControl(new maplibregl.NavigationControl());

// Сессию не ждём до load карты — меню пользователя появляется раньше.
loadSessionContext().catch(() => {});

// Когда стиль карты готов — подгружаем поля и вешаем обработчики на UI.
map.on('load', async () => {
    await loadFields();
    setupLayerControls();
});

/**
 * GeoJSON полей организации с бэкенда, отдельный source + два слоя (заливка и контур).
 * generateId: true — чтобы работал feature-state «selected» по числовому id фичи.
 */
async function loadFields() {
    const statusBox = document.getElementById('status-bar');
    try {
        const response = await fetch(apiPath("/get_fields"), fetchOpts);
        if (!response.ok) {
            throw new Error(`Ошибка загрузки полей: ${response.status}`);
        }
        const data = await response.json();
        loadedFieldsGeoJson = data;

        map.addSource('fields-source', {
            type: 'geojson',
            data: data,
            generateId: true
        });

        map.addLayer({
            id: 'fields-fill',
            type: 'fill',
            source: 'fields-source',
            paint: {
                'fill-color': '#ffffff',
                'fill-opacity': [
                    'case',
                    ['boolean', ['feature-state', 'selected'], false],
                    0.5, 0.1
                ]
            }
        });

        map.addLayer({
            id: 'fields-outline',
            type: 'line',
            source: 'fields-source',
            paint: {
                'line-color': '#ffffff',
                'line-width': 2
            }
        });

        if (data.features.length > 0) {
            const bounds = new maplibregl.LngLatBounds();
            data.features.forEach(f => expandBounds(bounds, f.geometry));
            try {
                map.fitBounds(bounds, { padding: 50 });
            } catch (_) { /* пустые или битые границы */ }
        }
        statusBox.innerText = "Поля загружены";
    } catch (err) {
        statusBox.innerText = "Ошибка загрузки полей";
    }
}

/** Переключение подложки, видимость полей и NDVI-слоя (если уже добавлен). */
function setupLayerControls() {
    document.querySelectorAll('input[name="base-layer"]').forEach(input => {
        input.addEventListener('change', (e) => {
            const val = e.target.value;
            map.setLayoutProperty('google-satellite', 'visibility', val === 'google' ? 'visible' : 'none');
            map.setLayoutProperty('osm', 'visibility', val === 'osm' ? 'visible' : 'none');
        });
    });

    document.getElementById('toggle-fields').addEventListener('change', (e) => {
        const state = e.target.checked ? 'visible' : 'none';
        map.setLayoutProperty('fields-fill', 'visibility', state);
        map.setLayoutProperty('fields-outline', 'visibility', state);
        if (map.getLayer(INTERSECTIONS_FILL_ID)) map.setLayoutProperty(INTERSECTIONS_FILL_ID, 'visibility', state);
        if (map.getLayer(INTERSECTIONS_OUTLINE_ID)) map.setLayoutProperty(INTERSECTIONS_OUTLINE_ID, 'visibility', state);
    });

    document.getElementById('toggle-ndvi').addEventListener('change', (e) => {
        const state = e.target.checked ? 'visible' : 'none';
        if (map.getLayer('ndvi-layer')) map.setLayoutProperty('ndvi-layer', 'visibility', state);
    });
}

function openFieldPopup(lngLat, props, history, isAgronomist, intersectingFeatures) {
    const key = String(props.id);
    popupFieldCache.set(key, {
        props: props,
        history: history,
        isAgronomist: !!isAgronomist,
        intersectingFeatures: Array.isArray(intersectingFeatures) ? intersectingFeatures : null
    });

    if (activePopup) {
        activePopup.remove();
    }
    const popup = new maplibregl.Popup({ closeButton: true, className: 'custom-popup' })
        .setLngLat(lngLat)
        .setMaxWidth('420px')
        .setHTML(fieldPopupHtml(props, history, isAgronomist, intersectingFeatures))
        .addTo(map);
    activePopup = popup;
    popup.on('close', () => {
        if (activePopup === popup) {
            activePopup = null;
        }
    });
}

function setIntersectingFieldsOnMap(data) {
    loadedIntersectingGeoJson = data && Array.isArray(data.features)
        ? data
        : { type: 'FeatureCollection', features: [] };

    if (!map.getSource(INTERSECTIONS_SOURCE_ID)) {
        map.addSource(INTERSECTIONS_SOURCE_ID, {
            type: 'geojson',
            data: loadedIntersectingGeoJson,
            generateId: true
        });
        map.addLayer({
            id: INTERSECTIONS_FILL_ID,
            type: 'fill',
            source: INTERSECTIONS_SOURCE_ID,
            paint: {
                'fill-color': '#ff6b6b',
                'fill-opacity': 0.25
            }
        });
        map.addLayer({
            id: INTERSECTIONS_OUTLINE_ID,
            type: 'line',
            source: INTERSECTIONS_SOURCE_ID,
            paint: {
                'line-color': '#ff3b30',
                'line-width': 3
            }
        });
        map.on('click', INTERSECTIONS_FILL_ID, (e) => {
            if (!e.features || e.features.length === 0) return;
            const feature = e.features[0];
            const props = feature.properties || {};
            const history = normalizeHistory(props.history);
            const statusBox = document.getElementById('status-bar');
            statusBox.innerText = 'Открыто пересекающееся поле.';
            openFieldPopup(e.lngLat, props, history, canOpenAgronomistPage && isFieldActive(props), null);
        });
    } else {
        map.getSource(INTERSECTIONS_SOURCE_ID).setData(loadedIntersectingGeoJson);
    }
}

function clearIntersectingFields() {
    setIntersectingFieldsOnMap({ type: 'FeatureCollection', features: [] });
}

// Клик по полигону: выделение, popup с краткой инфой и историей посевов.
map.on('click', 'fields-fill', (e) => {
    if (e.features.length > 0) {
        const feature = e.features[0];
        const props = feature.properties;
        const history = normalizeHistory(props.history);
        clearIntersectingFields();

        if (selectedMaplibreId !== null) {
            map.setFeatureState({ source: 'fields-source', id: selectedMaplibreId }, { selected: false });
        }
        selectedMaplibreId = feature.id;
        selectedFieldDbId = props.id;
        map.setFeatureState({ source: 'fields-source', id: selectedMaplibreId }, { selected: true });
        updateSelectedFieldInfo(props);
        updateAgronomistHistoryLink();

        const statusBox = document.getElementById('status-bar');
        statusBox.innerText = 'Поле выбрано — детали открыты во всплывающем окне.';

        openFieldPopup(e.lngLat, props, history, canOpenAgronomistPage && isFieldActive(props), null);
    }
});

/** NDVI одного выбранного поля на выбранную дату (кнопка в панели). */
async function updateNDVI() {
    if (!selectedFieldDbId) return alert("Выберите поле");
    const date = document.getElementById('date-input').value;

    try {
        const response = await fetch(
            `${apiPath("/get_ndvi_by_id")}?field_id=${encodeURIComponent(selectedFieldDbId)}&date=${encodeURIComponent(date)}`,
            fetchOpts
        );
        if (!response.ok) {
            alert(response.status === 404 ? "Поле недоступно" : `Ошибка ${response.status}`);
            return;
        }
        const data = await response.json();
        if (!data.url) throw new Error("Нет url");
        addNdviToMap(data.url, `NDVI поля ${selectedFieldDbId}`);
    } catch (err) { alert(err.message || "Ошибка NDVI"); }
}

/** Собираем id полей из загруженного GeoJSON (как на странице). */
function collectFieldIdsFromLoadedGeoJson() {
    if (!loadedFieldsGeoJson || !Array.isArray(loadedFieldsGeoJson.features)) return [];
    const ids = new Set();
    for (const f of loadedFieldsGeoJson.features) {
        const id = f.properties && f.properties.id;
        if (id != null && String(id).trim() !== "") ids.add(String(id).trim());
    }
    return [...ids];
}

/** NDVI по всей организации на дату. */
async function updateAllNDVI() {
    const date = document.getElementById('date-input').value;
    try {
        const params = new URLSearchParams();
        params.set("date", date);
        for (const id of collectFieldIdsFromLoadedGeoJson()) {
            params.append("field_ids", id);
        }
        const response = await fetch(
            `${apiPath("/get_all_ndvi_tile")}?${params.toString()}`,
            fetchOpts
        );
        if (!response.ok) {
            alert(`Ошибка ${response.status}`);
            return;
        }
        const data = await response.json();
        if (!data.url) throw new Error("Нет url");
        const org = (data.organizationName && String(data.organizationName).trim() !== "")
            ? data.organizationName
            : "организации";
        addNdviToMap(data.url, `NDVI всех полей (${org})`);
    } catch (err) { alert(err.message || "Ошибка NDVI"); }
}

/** Пересоздаём растровый слой NDVI под контуром полей (поле fields-outline в порядке слоёв). */
function addNdviToMap(url, label) {
    if (map.getLayer('ndvi-layer')) map.removeLayer('ndvi-layer');
    if (map.getSource('ndvi-source')) map.removeSource('ndvi-source');

    map.addSource('ndvi-source', { type: 'raster', tiles: [url], tileSize: 256 });
    map.addLayer({
        id: 'ndvi-layer',
        type: 'raster',
        source: 'ndvi-source',
        paint: { 'raster-opacity': 0.8 }
    }, 'fields-outline');

    document.getElementById('toggle-ndvi').checked = true;
    document.getElementById('status-bar').innerText = label + " загружен";
}

async function loadIntersectingFields(fieldId) {
    const response = await fetch(apiPath(`/api/fields/${encodeURIComponent(String(fieldId))}/intersections`), fetchOpts);
    if (!response.ok) {
        throw new Error(response.status === 404 ? 'Поле не найдено в организации.' : `Ошибка ${response.status}`);
    }
    return response.json();
}

document.addEventListener('click', async (e) => {
    const btn = e.target.closest('button[data-act="load-intersections"]');
    if (!btn) {
        return;
    }
    e.preventDefault();
    e.stopPropagation();

    const fieldId = btn.getAttribute('data-field-id');
    const context = popupFieldCache.get(String(fieldId));
    if (!fieldId || !context) {
        return;
    }

    const statusBox = document.getElementById('status-bar');
    btn.disabled = true;
    btn.textContent = 'Загрузка...';
    try {
        const data = await loadIntersectingFields(fieldId);
        const features = Array.isArray(data.features) ? data.features : [];
        setIntersectingFieldsOnMap(data);
        popupFieldCache.set(String(fieldId), {
            props: context.props,
            history: context.history,
            isAgronomist: context.isAgronomist,
            intersectingFeatures: features
        });
        if (activePopup) {
            activePopup.setHTML(fieldPopupHtml(
                context.props,
                context.history,
                context.isAgronomist,
                features
            ));
        }
        statusBox.innerText = features.length > 0
            ? `Загружено пересекающихся полей: ${features.length}.`
            : 'Пересекающихся полей не найдено.';
    } catch (err) {
        statusBox.innerText = err && err.message ? err.message : 'Не удалось загрузить пересекающиеся поля.';
    } finally {
        btn.disabled = false;
        btn.textContent = 'Показать истории пересекающихся полей';
    }
});

document.getElementById('btn-update').addEventListener('click', updateNDVI);
document.getElementById('btn-update-all').addEventListener('click', updateAllNDVI);

/** Расширяем bounds по кольцу полигона / первому кольцу каждого полигона в MultiPolygon. */
function expandBounds(bounds, geometry) {
    if (!geometry || !geometry.coordinates) return;

    if (geometry.type === 'Polygon') {
        geometry.coordinates[0].forEach(coord => bounds.extend(coord));
        return;
    }

    if (geometry.type === 'MultiPolygon') {
        geometry.coordinates.forEach(polygon => {
            polygon[0].forEach(coord => bounds.extend(coord));
        });
    }
}

/**
 * История в GeoJSON иногда приходит строкой JSON из БД — приводим к массиву объектов для popup.
 */
function normalizeHistory(rawHistory) {
    if (!rawHistory) return [];
    if (Array.isArray(rawHistory)) return rawHistory;

    try {
        const parsed = JSON.parse(rawHistory);
        return Array.isArray(parsed) ? parsed : [];
    } catch (e) {
        return [];
    }
}

function isFieldActive(props) {
    if (!props) return true;
    return props.active !== false && props.active !== 'false';
}

/** Экранирование перед вставкой в innerHTML, чтобы свойства поля не ломали разметку и XSS. */
function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

/** Верхняя часть popup: имя поля, id, площадь, число записей истории. */
function fieldSummaryHtml(props, historyCount) {
    const name = escapeHtml(props.name || 'Без имени');
    const id = escapeHtml(props.id);
    const area = escapeHtml(formatNumber(props.area));
    const status = isFieldActive(props) ? 'активное' : 'устаревшее';
    return `
        <div style="line-height: 1.55; font-size: 13px;">
            <strong style="font-size: 14px; display: block; margin-bottom: 4px;">${name}</strong>
            <span><strong>ID:</strong> ${id}</span><br>
            <span><strong>Площадь:</strong> ${area} га</span><br>
            <span><strong>Статус:</strong> ${status}</span><br>
            <span><strong>Записей истории:</strong> ${historyCount}</span>
        </div>
    `;
}

/** Список записей истории посева в popup. */
function fieldHistoryItemsHtml(history) {
    if (history.length === 0) {
        return `<div style="opacity: 0.8;">По этому полю пока нет истории.</div>`;
    }

    return history.map((item, index) => {
        const ndvi = item.ndviUrl
            ? `<a href="${escapeHtml(item.ndviUrl)}" target="_blank" rel="noopener noreferrer">открыть</a>`
            : '--';
        return `
            <div style="padding: 8px 0; border-bottom: 1px solid rgba(0,0,0,0.12); line-height: 1.45;">
                <strong>Запись ${index + 1}</strong><br>
                Год: ${escapeHtml(item.cropYear ?? '--')}<br>
                Культура: ${escapeHtml(item.cropName ?? '--')}<br>
                Посев: ${escapeHtml(item.sowingDate ?? '--')}<br>
                Уборка: ${escapeHtml(item.harvestDate ?? '--')}<br>
                Факт. урожайность: ${formatNumber(item.actualYield)}<br>
                План. урожайность: ${formatNumber(item.plannedYield)}<br>
                Дата последней аналитики: ${escapeHtml(item.analyticsDate ?? '--')}<br>
                NDVI URL: ${ndvi}
            </div>
        `;
    }).join('');
}

function intersectingFieldsSectionHtml(features) {
    if (!Array.isArray(features)) {
        return '';
    }
    if (features.length === 0) {
        return `
            <div style="margin-top: 12px; padding-top: 10px; border-top: 1px solid rgba(0,0,0,0.15);">
                <div style="font-size: 12px; margin-bottom: 6px;"><strong>Пересекающиеся поля</strong></div>
                <div style="font-size: 12px; opacity: 0.8;">Пересечений не найдено.</div>
            </div>
        `;
    }
    const body = features.map((feature) => {
        const props = feature.properties || {};
        const history = normalizeHistory(props.history);
        const title = escapeHtml(props.name || ('Поле #' + String(props.id || '—')));
        const status = isFieldActive(props) ? 'активное' : 'устаревшее';
        return `
            <div style="padding: 8px 0; border-bottom: 1px solid rgba(0,0,0,0.12);">
                <div style="font-size: 12px; line-height: 1.45; margin-bottom: 6px;">
                    <strong>${title}</strong><br>
                    ID: ${escapeHtml(props.id)} · Статус: ${escapeHtml(status)} · Площадь: ${escapeHtml(formatNumber(props.area))} га
                </div>
                <div style="font-size: 12px;">
                    ${fieldHistoryItemsHtml(history)}
                </div>
            </div>
        `;
    }).join('');
    return `
        <div style="margin-top: 12px; padding-top: 10px; border-top: 1px solid rgba(0,0,0,0.15);">
            <div style="font-size: 12px; margin-bottom: 6px;"><strong>Пересекающиеся поля</strong></div>
            <div style="max-height: 260px; overflow-y: auto; padding-right: 4px; font-size: 12px;">
                ${body}
            </div>
            <div style="margin-top: 8px; font-size: 11px; opacity: 0.8;">
                Контуры пересекающихся полей подсвечены на карте. На них тоже можно нажать.
            </div>
        </div>
    `;
}

/** Сборка HTML целого popup из сводки и истории. */
function fieldPopupHtml(props, history, isAgronomist, intersectingFeatures) {
    const summary = fieldSummaryHtml(props, history.length);
    const items = fieldHistoryItemsHtml(history);
    const fieldId = escapeHtml(props.id);
    const intersectionsButton = `
        <div style="margin-top: 12px; padding-top: 10px; border-top: 1px solid rgba(0,0,0,0.15); font-size: 13px;">
            <button type="button"
                    data-act="load-intersections"
                    data-field-id="${fieldId}"
                    style="display: inline-block; border: 1px solid #2d6cdf; background: #2d6cdf; color: #fff; border-radius: 6px; padding: 6px 10px; cursor: pointer; font-size: 12px;">
                Показать истории пересекающихся полей
            </button>
        </div>
    `;
    const editHistoryUrl = isAgronomist
        ? escapeHtml(apiPath("/org/agronomist") + "?fieldId=" + encodeURIComponent(String(props.id)))
        : "";
    const agronomistBlock = isAgronomist
        ? `<div style="margin-top: 12px; padding-top: 10px; border-top: 1px solid rgba(0,0,0,0.15); font-size: 13px;">
                <a href="${editHistoryUrl}" style="font-weight: 600;">Редактировать историю посевов этого поля</a>
            </div>`
        : "";
    const intersectionsBlock = intersectingFieldsSectionHtml(intersectingFeatures);
    return `
        <div style="padding: 8px 10px; color: #333; width: 380px; max-width: 100%;">
            <div style="margin-bottom: 10px; padding-bottom: 8px; border-bottom: 1px solid rgba(0,0,0,0.15);">
                ${summary}
            </div>
            <div style="font-size: 12px; margin-bottom: 6px;"><strong>История поля</strong></div>
            <div style="max-height: 300px; overflow-y: auto; padding-right: 4px; font-size: 12px;">
                ${items}
            </div>
            ${intersectionsButton}
            ${intersectionsBlock}
            ${agronomistBlock}
        </div>
    `;
}

/** Числа в popup: две цифры после запятой или прочерк. */
function formatNumber(value) {
    if (value === null || value === undefined || value === '') return '--';
    const num = Number(value);
    if (Number.isNaN(num)) return '--';
    return num.toFixed(2);
}
