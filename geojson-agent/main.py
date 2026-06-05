import os, json, re, geopandas as gpd
from pathlib import Path
from ai_mapper import get_mapping

TARGET_SCHEMA = """
{"name":"string","area":"number","active":"boolean","history":[{
  "cropName":"string","sowingDate":"string","harvestDate":"string",
  "sownAreaHa":"number","harvestAreaHa":"number","actualYield":"number",
  "totalYield":"number","plannedYield":"number","forecastedYield":"number",
  "sourceData":"string","sowingDetails":"string","cropYear":"number"
}]}"""

def analyze_columns_for_ai(gdf):
    info_lines = []
    for col in gdf.columns:
        if col == 'geometry': continue
        dtype = str(gdf[col].dtype)
        nunique = gdf[col].nunique()
        samples = [str(x) for x in gdf[col].dropna().head(3).tolist()]
        info_lines.append(f"- {col} (type: {dtype}, unique: {nunique}, samples: {samples})")
    return "\n".join(info_lines)

def safe_get(row, col_name, default=""):
    if col_name and col_name in row.index:
        val = row[col_name]
        if isinstance(val, float) and val != val: 
            return default
        return val
    return default

def to_float(val, default=0.0):
    try: return float(val)
    except (ValueError, TypeError): return default

def to_bool(val):
    if isinstance(val, bool): return val
    if isinstance(val, (int, float)): return val != 0
    return str(val).strip().lower() in ("1", "true", "yes", "да", "active")

def extract_crop_year(row, clean_mapping):
    # 1. Прямое маппирование
    for k in ["cropYear", "history.cropYear"]:
        if k in clean_mapping:
            src = clean_mapping[k]
            if src in row.index:
                try: return int(float(row[src]))
                except: pass

    # 2. Поиск года в любых полях, куда ИИ положил даты
    date_targets = ["sowingDate", "harvestDate", "date_act", "reg_date", "date_sow", "analyticsDate"]
    for target in date_targets:
        for k in [target, f"history.{target}"]:
            if k in clean_mapping:
                src = clean_mapping[k]
                if src in row.index:
                    m = re.search(r'(19|20)\d{2}', str(row[src]))
                    if m: return int(m.group(0))

    # 3. Жёсткий фоллбэк на известную колонку из твоих данных
    if "date_act" in row.index:
        m = re.search(r'(19|20)\d{2}', str(row["date_act"]))
        if m: return int(m.group(0))

    return 0

def process_shapefile_logic(shp_path: Path, out_path: Path, ollama_url: str = None, model: str = None):
    """Конвертация shapefile → GeoJSON с ИИ-маппингом (используется API worker и CLI)."""
    if ollama_url:
        os.environ["OLLAMA_BASE_URL"] = ollama_url
    if model:
        os.environ["MODEL_NAME"] = model

    print("📦 Читаю shapefile...")
    gdf = gpd.read_file(shp_path)
    gdf_metric = gdf.to_crs("EPSG:3857")
    gdf["area_calc"] = gdf_metric.geometry.area / 10000
    gdf_geojson = gdf.to_crs("EPSG:4326")

    print("🤖 Запрашиваю ИИ-маппинг...")
    cols_info = analyze_columns_for_ai(gdf)
    mapping = get_mapping(cols_info, TARGET_SCHEMA)
    print("✅ Маппинг:", json.dumps(mapping, indent=2, ensure_ascii=False))

    clean_mapping = {k: v for k, v in mapping.items() if str(v).lower() != "null"}

    features = []
    crops_set = set()

    for idx, row in gdf_geojson.iterrows():
        def gv(target_field, default=""):
            src = clean_mapping.get(target_field) or clean_mapping.get(f"history.{target_field}")
            if not src:
                return default
            return safe_get(row, src, default)

        crop_name_raw = str(gv("cropName", "")).strip()
        if crop_name_raw and crop_name_raw.lower() not in ("", "nan", "none"):
            crops_set.add(crop_name_raw)

        props = {
            "name": str(gv("name", f"Поле_{idx+1}")),
            "area": to_float(gv("area", row.get("area_calc", 0))),
            "active": to_bool(gv("active", "1")),
            "history": [{
                "cropName": crop_name_raw,
                "sowingDate": str(gv("sowingDate", "")),
                "harvestDate": str(gv("harvestDate", "")),
                "sownAreaHa": to_float(gv("sownAreaHa", 0)),
                "harvestAreaHa": to_float(gv("harvestAreaHa", row.get("area_calc", 0))),
                "actualYield": to_float(gv("actualYield", 0)),
                "totalYield": to_float(gv("totalYield", 0)),
                "plannedYield": to_float(gv("plannedYield", 0)),
                "forecastedYield": to_float(gv("forecastedYield", 0)),
                "sourceData": str(gv("sourceData", "")),
                "sowingDetails": str(gv("sowingDetails", "")),
                "cropYear": extract_crop_year(row, clean_mapping),
            }]
        }
        geom = row.geometry
        geom_json = dict(geom.__geo_interface__) if geom and not geom.is_empty else {"type":"Polygon","coordinates":[]}
        features.append({"type":"Feature","properties":props,"geometry":geom_json})

    result = {
        "fields": {"type":"FeatureCollection","features":features},
        "crops": [{"cropName": c} for c in sorted(crops_set)]
    }

    out_path = Path(out_path)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(result, f, indent=2, ensure_ascii=False)
    print(f"🎉 Готово! Результат: {out_path}")
    return out_path

def main():
    shp_path = Path("data/fields.shp")
    if not shp_path.exists():
        print("❌ Положи shapefile в папку ./data/")
        return
    process_shapefile_logic(shp_path, Path("output/result.geojson"))

if __name__ == "__main__":
    main()