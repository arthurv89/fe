package nl.arthurvlug.interviews.fedex.apiwrapper;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Map;

public class PricingApiWrapper extends ApiWrapper<Float> {
    private static final TypeReference<Map<String, Float>> typeReference = new TypeReference<>() {};

    @Override
    protected TypeReference<Map<String, Float>> getTypeReference() {
        return typeReference;
    }

    @Override
    protected String getPath() {
        return "/pricing";
    }
}
