import os, json, requests, time

OLLAMA_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
MODEL = os.getenv("MODEL_NAME", "qwen2.5:14b")

def get_mapping(cols_info: str, target_schema: str) -> dict:
    for i in range(15):
        try:
            requests.get(f"{OLLAMA_URL}/api/version", timeout=2)
            break
        except Exception:
            time.sleep(2)
    else:
        raise RuntimeError("Ollama не ответил. Проверь: docker compose logs ollama")

    prompt = f"""Ты эксперт по агро-GIS. Сопоставь ЦЕЛЕВЫЕ поля с ИСХОДНЫМИ колонками shapefile.
Верни ТОЛЬКО плоский JSON: {{"target_field": "source_column", ...}}

ЦЕЛЕВЫЕ ПОЛЯ: {target_schema}
ИСХОДНЫЕ КОЛОНКИ (статистика + примеры): {cols_info}

ПРАВИЛА:
1. name: Колонка с БОЛЬШИМ unique (ID, кадастр, код). НЕ название региона.
2. area: Числовая площадь.
3. active: 1 - по умолчанию, если поле вышло из эксплуатации: 0.
4. history.*: Урожай, даты, культура.
5. cropYear: Если есть отдельная колонка года → укажи её. Если нет → верни null.
6. Верни ТОЛЬКО JSON, без markdown."""

    payload = {
        "model": MODEL, 
        "messages": [{"role": "user", "content": prompt}],
        "stream": False, 
        "format": "json", 
        "options": {"temperature": 0.1}
    }

    print(f"⏳ Отправляю запрос в {MODEL}...")
    try:
        r = requests.post(f"{OLLAMA_URL}/api/chat", json=payload, timeout=180)
        r.raise_for_status()
        return json.loads(r.json()["message"]["content"])
    except requests.exceptions.ReadTimeout:
        raise RuntimeError("Таймаут. Модель грузится в VRAM. Повтори через 30 сек.")
    except Exception as e:
        raise RuntimeError(f"Ошибка запроса: {e}")