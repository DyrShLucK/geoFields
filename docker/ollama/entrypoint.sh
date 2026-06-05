#!/bin/sh
set -e

MODEL="${OLLAMA_MODEL:-qwen2.5:14b}"

echo "Запуск Ollama..."
ollama serve &
SERVE_PID=$!

echo "Ожидание API Ollama..."
i=0
while [ "$i" -lt 90 ]; do
  if ollama list >/dev/null 2>&1; then
    break
  fi
  i=$((i + 1))
  sleep 2
done

if ! ollama list >/dev/null 2>&1; then
  echo "Ollama API не поднялся за 3 минуты"
  exit 1
fi

if ollama show "$MODEL" >/dev/null 2>&1; then
  echo "Модель $MODEL уже установлена"
else
  echo "Скачивание $MODEL (первый запуск ~9 ГБ, 10–30 мин)..."
  ollama pull "$MODEL"
  echo "Модель $MODEL готова"
fi

wait "$SERVE_PID"
