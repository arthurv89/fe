package nl.arthurvlug.interviews.fedex.apiwrapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static nl.arthurvlug.interviews.fedex.AggregationService.waitForAllFutures;

public abstract class ApiWrapper<O> {
    private static final String host = "http://localhost:8080";
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final int queueCap = 5;
    private static final int schedulingPeriod = 5; // In seconds

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final Map<String, List<CompletableFuture<Optional<O>>>> listeners = new ConcurrentHashMap<>();
    private ScheduledFuture<?> scheduledFuture;
    private static final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

    public synchronized Map<String, CompletableFuture<Optional<O>>> futures(final Set<String> inputSet) {
        if (inputSet.isEmpty()) {
            return ImmutableMap.of();
        }
        Map<String, CompletableFuture<Optional<O>>> futures = registerListeners(inputSet);

        if (queue.isEmpty()) {
            queue.addAll(inputSet);
            resetSchedule();
        } else {
            queue.addAll(inputSet);
        }

        if (queue.size() >= queueCap) {
            getDataFromService(takeBatchFromQueue());
        }
        return futures;
    }

    private CompletableFuture<Optional<Map<String, O>>> getDataFromService(final Set<String> inputSet) {
        return getFromService(getPath(), inputSet)
                .thenApply(Optional::ofNullable)
                .thenApply(response -> {
                    notifyListeners(inputSet, response);
                    if(!queue.isEmpty()) {
                        resetSchedule();
                    }
                    return response;
                });
    }

    private Map<String, CompletableFuture<Optional<O>>> registerListeners(final Set<String> inputSet) {
        return inputSet.stream()
                .collect(Collectors.toMap(
                        x -> x,
                        x -> {
                            if (!listeners.containsKey(x)) {
                                listeners.put(x, new CopyOnWriteArrayList<>());
                            }
                            final CompletableFuture<Optional<O>> future = new CompletableFuture<>();
                            listeners.get(x).add(future);
                            return future;
                        })
                );
    }

    private void notifyListeners(
            final Set<String> inputSet,
            final Optional<Map<String, O>> optionalResponse
    ) {
        inputSet.forEach(key -> {
            final List<CompletableFuture<Optional<O>>> listenersForKey = listeners.get(key);
            if (listenersForKey != null) {
                final Optional<O> value = optionalResponse.map(response -> response.get(key));
                listenersForKey.forEach(listener -> {
                    listener.complete(value);
                });
                listeners.remove(key);
            };
        });
    }

    private CompletableFuture<Map<String, O>> getFromService(final String path, final Set<String> inputSet) {
        return executeGetCall(path + "?q=" + joinItems(inputSet))
                .thenApply(json -> parseJson(json, getTypeReference()));
    }

    private void resetSchedule() {
        if (this.scheduledFuture != null) {
            this.scheduledFuture.cancel(false);
        }
        this.scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::executeScheduledCall, schedulingPeriod, schedulingPeriod, SECONDS);

    }

    private void executeScheduledCall() {
        if (queue.isEmpty()) {
            return;
        }
        final List<CompletableFuture<?>> batches = new ArrayList<>();
        do {
            final Set<String> items = takeBatchFromQueue();

            batches.add(this.getDataFromService(items));
        } while (!queue.isEmpty());
        waitForAllFutures(batches);
    }

    private Set<String> takeBatchFromQueue() {
        return Arrays.stream(new Integer[queueCap])
                .map((i) -> {
                    try {
                        synchronized (queue) {
                            if (queue.isEmpty()) {
                                return null;
                            }
                            return queue.take();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public static <String, O> Map<String, Optional<O>> resolveFutures(final Map<String, CompletableFuture<Optional<O>>> futures) {
        return futures.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        x -> x.getKey(),
                        x -> {
                            try {
                                return x.getValue().get();
                            } catch (Exception e) {
                                e.printStackTrace();
                                throw new RuntimeException(e);
                            }
                        }
                ));
    }

    private String joinItems(final Collection<String> inputSet) {
        final List<String> sorted = new ArrayList<>(inputSet)
                .stream()
                .sorted()
                .collect(Collectors.toList());
        return Joiner.on(",").join(sorted);
    }

    private @Nullable <T> T parseJson(@Nullable final String json,
                                      final TypeReference<T> typeReference) {
        try {
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, typeReference);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private CompletableFuture<String> executeGetCall(final String path) {
        try {
            System.out.println("Calling " + path);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(host + path))
                    .GET()
                    .build();
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(this::handleBody);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Nullable
    private String handleBody(final HttpResponse<String> t) {
        final int code = t.statusCode();
        if (code >= 200 && code < 300) {
            return t.body();
        } else {
            return null;
        }
    }

    protected abstract TypeReference<Map<String, O>> getTypeReference();

    protected abstract String getPath();
}
