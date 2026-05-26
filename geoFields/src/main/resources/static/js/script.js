// =====================================================
// API
// =====================================================

const API_URL = "http://localhost:8080";

const _apiBase =
    String(API_URL || "")
        .trim()
        .replace(/\/$/, "");

const fetchOpts = {
    credentials: _apiBase
        ? "include"
        : "same-origin"
};

function apiPath(path) {

    const p =
        path.startsWith("/")
            ? path
            : "/" + path;

    return _apiBase
        ? _apiBase + p
        : p;
}

// =====================================================
// MAP
// =====================================================

const map = new maplibregl.Map({

    container: "map",

    style: {

        version: 8,

        sources: {

            "google-satellite": {

                type: "raster",

                tiles: [
                    "https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}"
                ],

                tileSize: 256
            },

            "osm": {

                type: "raster",

                tiles: [
                    "https://a.tile.openstreetmap.org/{z}/{x}/{y}.png"
                ],

                tileSize: 256,

                attribution: "© OpenStreetMap"
            }
        },

        layers: [

            {

                id: "osm",

                type: "raster",

                source: "osm",

                layout: {
                    visibility: "none"
                }
            },

            {

                id: "google-satellite",

                type: "raster",

                source: "google-satellite",

                layout: {
                    visibility: "visible"
                }
            }
        ]
    },

    center: [0, 0],

    zoom: 2
});

// =====================================================
// NAVIGATION
// =====================================================

map.addControl(
    new maplibregl.NavigationControl(),
    "top-right"
);

// =====================================================
// GLOBALS
// =====================================================

let loadedFieldsGeoJson = null;

// =====================================================
// LOAD FIELDS
// =====================================================

map.on("load", async () => {

    await loadFields();

    setupLayerControls();
});

async function loadFields() {

    try {

        const response =
            await fetch(
                apiPath("/get_fields"),
                fetchOpts
            );

        if (!response.ok) {
            throw new Error();
        }

        const data =
            await response.json();

        loadedFieldsGeoJson = data;

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

                "fill-color": "#ffffff",

                "fill-opacity": 0.12
            }
        });

        map.addLayer({

            id: "fields-outline",

            type: "line",

            source: "fields-source",

            paint: {

                "line-color": "#ffffff",

                "line-width": 2
            }
        });

        // =========================================
        // FIT TO FIELDS
        // =========================================

        if (
            data.features &&
            data.features.length > 0
        ) {

            const bounds =
                new maplibregl.LngLatBounds();

            data.features.forEach(feature => {

                expandBounds(
                    bounds,
                    feature.geometry
                );
            });

            map.fitBounds(bounds, {

                padding: 80,

                duration: 1500
            });
        }

    } catch (e) {

        console.error(
            "Ошибка загрузки полей",
            e
        );
    }
}

// =====================================================
// BOUNDS
// =====================================================

function expandBounds(bounds, geometry) {

    if (
        !geometry ||
        !geometry.coordinates
    ) {
        return;
    }

    if (geometry.type === "Polygon") {

        geometry.coordinates[0]
            .forEach(coord => {

                bounds.extend(coord);
            });
    }

    if (geometry.type === "MultiPolygon") {

        geometry.coordinates
            .forEach(polygon => {

                polygon[0]
                    .forEach(coord => {

                        bounds.extend(coord);
                    });
            });
    }
}

// =====================================================
// LAYER CONTROLS
// =====================================================

function setupLayerControls() {

    document
        .querySelectorAll(
            'input[name="base-layer"]'
        )
        .forEach(input => {

            input.addEventListener(
                "change",
                (e) => {

                    const value =
                        e.target.value;

                    map.setLayoutProperty(
                        "google-satellite",
                        "visibility",
                        value === "google"
                            ? "visible"
                            : "none"
                    );

                    map.setLayoutProperty(
                        "osm",
                        "visibility",
                        value === "osm"
                            ? "visible"
                            : "none"
                    );
                }
            );
        });

    const toggleFields =
        document.getElementById(
            "toggle-fields"
        );

    toggleFields?.addEventListener(
        "change",
        (e) => {

            const visibility =
                e.target.checked
                    ? "visible"
                    : "none";

            if (
                map.getLayer("fields-fill")
            ) {

                map.setLayoutProperty(
                    "fields-fill",
                    "visibility",
                    visibility
                );
            }

            if (
                map.getLayer("fields-outline")
            ) {

                map.setLayoutProperty(
                    "fields-outline",
                    "visibility",
                    visibility
                );
            }
        }
    );
}

// =====================================================
// SIDEBAR
// =====================================================

const sidebar =
    document.getElementById(
        "sidebar"
    );

const sidebarToggle =
    document.getElementById(
        "sidebar-toggle"
    );

sidebarToggle?.addEventListener(
    "click",
    () => {

        sidebar?.classList.toggle(
            "open"
        );
    }
);

// =====================================================
// ANALYTICS PANEL
// =====================================================

const analyticsPanel =
    document.getElementById(
        "analytics-panel"
    );

const analyticsToggle =
    document.getElementById(
        "analytics-toggle"
    );

const analyticsClose =
    document.getElementById(
        "analytics-close"
    );

analyticsToggle?.addEventListener(
    "click",
    () => {

        analyticsPanel?.classList.add(
            "open"
        );
    }
);

analyticsClose?.addEventListener(
    "click",
    () => {

        analyticsPanel?.classList.remove(
            "open"
        );
    }
);

// =====================================================
// LAYERS PANEL
// =====================================================

const layersPanel =
    document.getElementById(
        "layers-panel"
    );

const layersToggle =
    document.getElementById(
        "layers-toggle"
    );

layersToggle?.addEventListener(
    "click",
    () => {

        layersPanel?.classList.toggle(
            "open"
        );
    }
);

// =====================================================
// FILTER PANEL
// =====================================================

const filterPanel =
    document.getElementById(
        "filter-panel"
    );

const filterToggle =
    document.getElementById(
        "filter-toggle"
    );

filterToggle?.addEventListener(
    "click",
    () => {

        filterPanel?.classList.toggle(
            "open"
        );
    }
);

// =====================================================
// LEFT CONTROL BUTTONS
// =====================================================

const leftButtons =
    document.querySelectorAll(
        ".map-controls-left .floating-btn"
    );

// FIT TO FIELDS

leftButtons[1]?.addEventListener(
    "click",
    () => {

        if (
            !loadedFieldsGeoJson ||
            !loadedFieldsGeoJson.features
        ) {
            return;
        }

        const bounds =
            new maplibregl.LngLatBounds();

        loadedFieldsGeoJson.features
            .forEach(feature => {

                expandBounds(
                    bounds,
                    feature.geometry
                );
            });

        map.fitBounds(bounds, {

            padding: 80,

            duration: 1200
        });
    }
);

// GEOLOCATION

leftButtons[2]?.addEventListener(
    "click",
    () => {

        navigator.geolocation
            .getCurrentPosition(
                (position) => {

                    map.flyTo({

                        center: [

                            position.coords.longitude,

                            position.coords.latitude

                        ],

                        zoom: 14
                    });
                }
            );
    }
);

// MEASURE

leftButtons[3]?.addEventListener(
    "click",
    () => {

        alert(
            "Инструмент измерения пока не подключен"
        );
    }
);