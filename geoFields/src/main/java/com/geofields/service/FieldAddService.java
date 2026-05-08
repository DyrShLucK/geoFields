package com.geofields.service;

import com.geofields.dto.agronomist.ActionMessageResponse;
import com.geofields.dto.agronomist.NewFieldIntakeRequest;
import com.geofields.repository.FieldCropHistoryRepository;
import com.geofields.repository.FieldRepository;
import com.geofields.repository.row.FieldHistoryRow;
import com.geofields.repository.row.FieldOptionRow;
import com.geofields.security.GeoFieldsUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FieldAddService {

    private static final Logger log = LoggerFactory.getLogger(FieldGeoJsonQueryService.class);

    public FieldAddService(FieldRepository fieldRepository) {
        this.fieldRepository = fieldRepository;
    }

    private FieldRepository fieldRepository;


    public ActionMessageResponse AddField(NewFieldIntakeRequest field, GeoFieldsUserDetails user){
        List<FieldHistoryRow> listfields = fieldRepository.findAllFieldsWithHistory(user.getOrganizationId());
        log.info(listfields.toString());
        return new ActionMessageResponse("OK");
    }
}
