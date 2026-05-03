/**
 * Главная страница с картой (MapLibre): поля организации, NDVI, боковая панель пользователя.
 * Запросы к своему же origin с cookie сессии — бэкенд знает организацию и пользователя.
 */

// Префикс URL бэкенда (пустой = тот же хост, что у страницы).
const API_URL_PYTHON = "localhost:8000"; // заготовка, если когда-нибудь дернуть другой хост вручную
const API_URL = "";
// Без этого fetch не отправит cookie сессии на тот же сайт.
const fetchOpts = { credentials: "same-origin" };

/**
 * Подтягивает данные текущего пользователя с API (HTML главной без Thymeleaf).
 * Заполняет блок меню, ссылки менеджера/админа, скрытое поле CSRF для POST /logout.
 */
async function loadSessionContext() {
    const res = await fetch("/api/session/context", fetchOpts);
    if (!res.ok) {
        if (res.status === 401) {
            window.location.href = "/login";
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
}

// id поля в БД (из properties фичи GeoJSON) — для запросов NDVI по одному полю.
let selectedFieldDbId = null;
// Внутренний id объекта на карте (feature-state «выбран») — для подсветки полигона.
let selectedMaplibreId = null;

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
        const response = await fetch(`/get_fields`, fetchOpts);
        if (!response.ok) {
            throw new Error(`Ошибка загрузки полей: ${response.status}`);
        }
        const data = await response.json();

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
    });

    document.getElementById('toggle-ndvi').addEventListener('change', (e) => {
        const state = e.target.checked ? 'visible' : 'none';
        if (map.getLayer('ndvi-layer')) map.setLayoutProperty('ndvi-layer', 'visibility', state);
    });
}

// Клик по полигону: выделение, popup с краткой инфой и историей посевов.
map.on('click', 'fields-fill', (e) => {
    if (e.features.length > 0) {
        const feature = e.features[0];
        const props = feature.properties;
        const history = normalizeHistory(props.history);

        if (selectedMaplibreId !== null) {
            map.setFeatureState({ source: 'fields-source', id: selectedMaplibreId }, { selected: false });
        }
        selectedMaplibreId = feature.id;
        selectedFieldDbId = props.id;
        map.setFeatureState({ source: 'fields-source', id: selectedMaplibreId }, { selected: true });

        const statusBox = document.getElementById('status-bar');
        statusBox.innerText = 'Поле выбрано — детали открыты во всплывающем окне.';

        new maplibregl.Popup({ closeButton: true, className: 'custom-popup' })
            .setLngLat(e.lngLat)
            .setMaxWidth('420px')
            .setHTML(fieldPopupHtml(props, history))
            .addTo(map);
    }
});

/** NDVI одного выбранного поля на выбранную дату (кнопка в панели). */
async function updateNDVI() {
    if (!selectedFieldDbId) return alert("Выберите поле");
    const date = document.getElementById('date-input').value;

    try {
        const response = await fetch(
            `${API_URL}/get_ndvi_by_id?field_id=${encodeURIComponent(selectedFieldDbId)}&date=${encodeURIComponent(date)}`,
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

/** NDVI по всей организации на дату. */
async function updateAllNDVI() {
    const date = document.getElementById('date-input').value;
    try {
        const response = await fetch(
            `${API_URL}/get_all_ndvi_tile?date=${encodeURIComponent(date)}`,
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
    return `
        <div style="line-height: 1.55; font-size: 13px;">
            <strong style="font-size: 14px; display: block; margin-bottom: 4px;">${name}</strong>
            <span><strong>ID:</strong> ${id}</span><br>
            <span><strong>Площадь:</strong> ${area} га</span><br>
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

/** Сборка HTML целого popup из сводки и истории. */
function fieldPopupHtml(props, history) {
    const summary = fieldSummaryHtml(props, history.length);
    const items = fieldHistoryItemsHtml(history);
    return `
        <div style="padding: 8px 10px; color: #333; width: 380px; max-width: 100%;">
            <div style="margin-bottom: 10px; padding-bottom: 8px; border-bottom: 1px solid rgba(0,0,0,0.15);">
                ${summary}
            </div>
            <div style="font-size: 12px; margin-bottom: 6px;"><strong>История поля</strong></div>
            <div style="max-height: 300px; overflow-y: auto; padding-right: 4px; font-size: 12px;">
                ${items}
            </div>
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
