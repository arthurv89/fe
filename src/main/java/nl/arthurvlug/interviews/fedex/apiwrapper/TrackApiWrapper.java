package nl.arthurvlug.interviews.fedex.apiwrapper;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Map;

public class TrackApiWrapper extends ApiWrapper<String> {
    private static final TypeReference<Map<String, String>> typeReference = new TypeReference<>() {};

    @Override
    protected TypeReference<Map<String, String>> getTypeReference() {
        return typeReference;
    }

    @Override
    protected String getPath() {
        return "/track";
    }
}
