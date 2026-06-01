package com.geofields.service;

import com.geofields.repository.FieldCropHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/** Поиск и создание культур по названию (импорт shapefile). */
@Service
public class CropLookupService {

    private final FieldCropHistoryRepository fieldCropHistoryRepository;

    public CropLookupService(FieldCropHistoryRepository fieldCropHistoryRepository) {
        this.fieldCropHistoryRepository = fieldCropHistoryRepository;
    }

    /** Сессия с кэшем названий в рамках одной операции импорта. */
    public CropResolveSession openSession() {
        return new CropResolveSession(fieldCropHistoryRepository);
    }

    public static final class CropResolveSession {
        private final FieldCropHistoryRepository repository;
        private final Map<String, Long> cache = new HashMap<>();
        private int createdCount;

        private CropResolveSession(FieldCropHistoryRepository repository) {
            this.repository = repository;
        }

        public Long resolve(String cropName) {
            if (cropName == null || cropName.isBlank()) {
                return null;
            }
            String key = cropName.trim().toLowerCase();
            Long cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
            long cropId = repository.findCropIdByName(cropName)
                    .orElseGet(() -> {
                        createdCount++;
                        return repository.insertCrop(cropName);
                    });
            cache.put(key, cropId);
            return cropId;
        }

        public int createdCount() {
            return createdCount;
        }
    }
}
