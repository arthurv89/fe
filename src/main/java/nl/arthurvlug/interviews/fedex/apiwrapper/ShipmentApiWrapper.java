package nl.arthurvlug.interviews.fedex.apiwrapper;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

public class ShipmentApiWrapper extends ApiWrapper<List<String>> {
    private static final TypeReference<Map<String, List<String>>> typeReference = new TypeReference<>() {};

    protected TypeReference<Map<String, List<String>>> getTypeReference() {
        return typeReference;
    }

    protected String getPath() {
        return "/shipments";
    }
}
