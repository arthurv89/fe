import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.common.collect.ImmutableMap;
import nl.arthurvlug.interviews.fedex.Aggregation;
import nl.arthurvlug.interviews.fedex.Application;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static java.time.Duration.ZERO;
import static nl.arthurvlug.interviews.fedex.apiwrapper.ApiWrapper.timeout;
import static org.assertj.core.api.Assertions.assertThat;

public class AggregationTest {
    final String hostname = "http://localhost:8081";
    final int schedulerPeriod = 5000; // ms

    private final WireMockServer wireMockServer = new WireMockServer(8080);
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private ConfigurableApplicationContext applicationContext;

    @BeforeEach
    public void beforeEach() {
        applicationContext = Application.startApplication(new String[0]);
        wireMockServer.resetAll();
        wireMockServer.start();
    }

    @AfterEach
    public void afterEach() {
        applicationContext.close();
        wireMockServer.stop();
    }

    @Test
    public void testAllSuccess() throws Exception {
        stubShipments(TestData.smallShipmentsMap, 200, TestData.smallShipmentIds, ZERO);
        stubTrack(TestData.smallTrackMap, 200, TestData.smallTrackIds);
        stubPricing(TestData.smallPricingMap, 200, TestData.smallPricingIds);

        long startTime = System.currentTimeMillis();
        final Aggregation aggregation = aggregationCall(TestData.smallPricingIds, TestData.smallTrackIds, TestData.smallShipmentIds);
        long endTime = System.currentTimeMillis();

        assertThat(endTime - startTime).isGreaterThan(schedulerPeriod/2);
        assertThat(aggregation).isEqualTo(toAggregation(
                TestData.smallShipmentsMap,
                TestData.smallTrackMap,
                TestData.smallPricingMap
        ));
    }

    @Test
    public void testNotAllParameters() throws Exception {
        stubShipments(TestData.smallShipmentsMap, 200, TestData.smallShipmentIds, ZERO);
        stubTrack(TestData.smallTrackMap, 200, TestData.smallTrackIds);
        stubPricing(TestData.smallPricingMap, 200, TestData.smallPricingIds);

        final Aggregation aggregation = aggregationCall(
                null,
                null,
                null
        );
        assertThat(aggregation).isEqualTo(toAggregation(
                Map.of(),
                Map.of(),
                Map.of()
        ));
    }

    @Test
    public void testSomeSuccess_scheduler() throws Exception {
        stubShipments(null, 503, TestData.smallShipmentIds, ZERO);
        stubTrack(TestData.smallTrackMap, 200, TestData.smallTrackIds);
        stubPricing(TestData.smallPricingMap, 200, TestData.smallPricingIds);

        long startTime = System.currentTimeMillis();
        final Aggregation aggregation = aggregationCall(TestData.smallPricingIds, TestData.smallTrackIds, TestData.smallShipmentIds);
        long endTime = System.currentTimeMillis();

        assertThat(endTime - startTime).isGreaterThan(schedulerPeriod/2);
        assertThat(aggregation).isEqualTo(toAggregation(
                TestData.nullSmallShipmentsMap(),
                TestData.smallTrackMap,
                TestData.smallPricingMap
        ));
    }

    @Test
    public void testSomeSuccess_immediately() throws Exception {
        stubShipments(null, 503, TestData.bigShipmentIds, ZERO);
        stubTrack(null, 200, TestData.bigTrackIds);
        stubPricing(null, 200, TestData.bigPricingIds);

        long startTime = System.currentTimeMillis();
        aggregationCall(TestData.bigPricingIds, TestData.bigTrackIds, TestData.bigShipmentIds);
        long endTime = System.currentTimeMillis();

        assertThat(endTime - startTime).isLessThan(schedulerPeriod/2);

    }

    @Test
    public void testSlowAggregation_responseWithNulls() throws Exception {
        stubShipments(TestData.smallShipmentsMap, 200, TestData.smallShipmentIds, timeout.plusMillis(2000));
        stubTrack(TestData.smallTrackMap, 200, TestData.smallTrackIds);
        stubPricing(TestData.smallPricingMap, 200, TestData.smallPricingIds);

        final Aggregation aggregation = aggregationCall(TestData.smallPricingIds, TestData.smallTrackIds, TestData.smallShipmentIds);

        assertThat(aggregation).isEqualTo(new Aggregation(
                TestData.nullSmallShipmentsMap(),
                TestData.smallTrackMap,
                TestData.smallPricingMap));

    }

    private Aggregation toAggregation(final Map<String, List<String>> shipmentsMap,
                                      final Map<String, String> trackMap,
                                      final Map<String, Float> pricingMap) {
        return new Aggregation(shipmentsMap, trackMap, pricingMap);
    }

    private void stubShipments(final Map<String, List<String>> map, final int status, final String ids, final Duration delay) throws JsonProcessingException {
        stubGet("/shipments?q=" + ids, map, status, delay);
    }

    private void stubTrack(final Map<String, String> map, final int status, final String ids) throws JsonProcessingException {
        stubGet("/track?q=" + ids, map, status, ZERO);
    }

    private void stubPricing(final Map<String, Float> map, final int status, final String countryCodes) throws JsonProcessingException {
        stubGet("/pricing?q=" + countryCodes, map, status, ZERO);
    }

    private void stubGet(final String url, final Object responseBody, final int status, final Duration delay) throws JsonProcessingException {
        wireMockServer.stubFor(WireMock.get(url)
                .willReturn(aResponse()
                        .withStatus(status)
                        .withBody(objectMapper.writeValueAsString(responseBody))
                        .withFixedDelay((int) delay.toMillis())));
    }

    private Aggregation aggregationCall(
            @Nullable final String pricing,
            @Nullable final String track,
            @Nullable final String shipments
    ) throws URISyntaxException, IOException, InterruptedException {
        final ImmutableMap.Builder<String, String> mapBuilder = ImmutableMap.builder();
        if(pricing != null) { mapBuilder.put("pricing", pricing); }
        if(track != null) { mapBuilder.put("track", track); }
        if(shipments != null) { mapBuilder.put("shipments", shipments); }
        final Map<String, String> map = mapBuilder.build();
        final String queryParams = map.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));

        return aggregationCall(queryParams);
    }

    private Aggregation aggregationCall(final String queryParams) throws URISyntaxException, IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(hostname + "/aggregation?" + queryParams))
                .GET()
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
        final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        final String json = response.body();
        return objectMapper.readValue(json, Aggregation.class);
    }
}
