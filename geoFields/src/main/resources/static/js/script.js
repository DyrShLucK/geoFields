const API_URL = "http://127.0.0.1:8000";

let selectedFieldDbId = null;
let selectedMaplibreId = null;

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

map.on('load', async () => {
    await loadFields();
    setupLayerControls();
});

async function loadFields() {
    const statusBox = document.getElementById('status-bar');
    try {
        const response = await fetch(`${API_URL}/get_fields`);
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
            data.features.forEach(f => {
                const coords = f.geometry.type === 'Polygon' ? f.geometry.coordinates[0] : f.geometry.coordinates[0][0];
                coords.forEach(c => bounds.extend(c));
            });
            map.fitBounds(bounds, { padding: 50 });
        }
        statusBox.innerText = "Поля загружены";
    } catch (err) {
        statusBox.innerText = "Ошибка БД";
    }
}

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

map.on('click', 'fields-fill', (e) => {
    if (e.features.length > 0) {
        const feature = e.features[0];
        const props = feature.properties;

        if (selectedMaplibreId !== null) {
            map.setFeatureState({ source: 'fields-source', id: selectedMaplibreId }, { selected: false });
        }
        selectedMaplibreId = feature.id;
        selectedFieldDbId = props.id;
        map.setFeatureState({ source: 'fields-source', id: selectedMaplibreId }, { selected: true });

        const statusBox = document.getElementById('status-bar');
        statusBox.innerHTML = `
            <div style="line-height: 1.6">
                <strong>Поле:</strong> ${props.name || 'Без имени'}<br>
                <strong>ID:</strong> ${props.id}<br>
                <strong>Площадь:</strong> ${props.area || '--'} га
            </div>
        `;

        new maplibregl.Popup({ closeButton: true, className: 'custom-popup' })
            .setLngLat(e.lngLat)
            .setHTML(`
                <div style="padding: 5px; color: #333;">
                    <h3 style="margin: 0 0 5px 0; font-size: 14px;">${props.name || 'Поле'}</h3>
                    <p style="margin: 0; font-size: 12px;">
                        Площадь: <b>${props.area || '--'} га</b><br>
                        ID: ${props.id}
                    </p>
                </div>
            `)
            .addTo(map);

        console.log("Выбраны данные:", props);
    }
});

async function updateNDVI() {
    if (!selectedFieldDbId) return alert("Выберите поле");
    const date = document.getElementById('date-input').value;

    try {
        const response = await fetch(`${API_URL}/get_ndvi_by_id?field_id=${selectedFieldDbId}&date=${date}`);
        const data = await response.json();
        addNdviToMap(data.url, `NDVI ${selectedFieldDbId}`);
    } catch (err) { alert("Ошибка GEE"); }
}

async function updateAllNDVI() {
    const date = document.getElementById('date-input').value;
    try {
        const response = await fetch(`${API_URL}/get_all_ndvi_tile?date=${date}`);
        const data = await response.json();
        addNdviToMap(data.url, "Весь массив");
    } catch (err) { alert("Ошибка GEE"); }
}

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