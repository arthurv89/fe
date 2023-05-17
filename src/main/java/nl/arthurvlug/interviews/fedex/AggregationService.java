package nl.arthurvlug.interviews.fedex;

import com.google.common.collect.ImmutableList;
import nl.arthurvlug.interviews.fedex.apiwrapper.PricingApiWrapper;
import nl.arthurvlug.interviews.fedex.apiwrapper.ShipmentApiWrapper;
import nl.arthurvlug.interviews.fedex.apiwrapper.TrackApiWrapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static nl.arthurvlug.interviews.fedex.apiwrapper.ApiWrapper.resolveFutures;

public class AggregationService {
    private final ShipmentApiWrapper shipmentApiWrapper = new ShipmentApiWrapper();
    private final TrackApiWrapper trackApiWrapper = new TrackApiWrapper();
    private final PricingApiWrapper pricingApiWrapper = new PricingApiWrapper();

    public Aggregation aggregate(
            final Set<String> shipmentIds,
            final Set<String> trackIds,
            final Set<String> countryCodes
    ) {
        final Map<String, CompletableFuture<Optional<List<String>>>> shipmentFutures = shipmentApiWrapper.futures(shipmentIds);
        final Map<String, CompletableFuture<Optional<String>>> trackFutures = trackApiWrapper.futures(trackIds);
        final Map<String, CompletableFuture<Optional<Float>>> pricingFutures = pricingApiWrapper.futures(countryCodes);

        final List<CompletableFuture<?>> futures = ImmutableList.<CompletableFuture<?>> builder()
                .addAll(shipmentFutures.values())
                .addAll(trackFutures.values())
                .addAll(pricingFutures.values())
                .build();
        waitForAllFutures(futures);

        final Map<String, Optional<List<String>>> shipmentMap = resolveFutures(shipmentFutures);
        final Map<String, Optional<String>> trackMap = resolveFutures(trackFutures);
        final Map<String, Optional<Float>> pricingMap = resolveFutures(pricingFutures);

        return new Aggregation(toNullableMap(shipmentMap), toNullableMap(trackMap), toNullableMap(pricingMap));
    }

    private <K, V> Map<K, V> toNullableMap(final Map<K, Optional<V>> map) {
        final Map<K, V> result = new HashMap<>();
        for(Map.Entry<K, Optional<V>> e : map.entrySet()) {
            result.put(e.getKey(), e.getValue().orElse(null));
        }
        return result;
    }

    public static void waitForAllFutures(List<CompletableFuture<?>> futures) {
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }
}
