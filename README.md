# Instructions

#### Run docker container using:

```docker run -p 8080:8080 xyzassessment/backend-services:latest```

#### Run the test using Maven, or using the debugger in your IDE

#### Run the application using:

```mvn spring-boot:run```
The application runs on port 8081 since port 8080 is already in use by the given Docker container

# Documentation

The application is a simple Spring Boot application.
The controller splits the input data and passes it to the Aggregation Service.
There, the three different API calls are transformed into futures. Only if all the futures are completed, the request will complete.
The results are then converted to an Aggregation object, which is then converted to JSON by Spring Boot.

As the requirements describe, the call to the other APIs can be done immediately (if the queue sizes are all 5), or they can be added to the queue to be executed later.
The method returns empty futures for each item in the request that the request thread can wait for.
The future itself is added to the listeners map so the scheduler can complete the future later.
Multiple requests can wait for the same data, so when the data arrives, multiple requests can be notified.

Only the APIWrapper class contains data that multiple threads can access at the same time, so this is the only place where we have to manage concurrency.
The queues are Blocking Queues, since they have to support multiple threads adding data to the queue.
The listeners map is also thread-safe since it's a ConcurrentHashMap.
For simplicity, the whole method to generate futures is synchronized, since there is non-atomic logic there.
The only other public method is resolveFutures(...), which doesn't need synchronization.

The tests are created using Wiremock.
Each test spins up the application, and tears it down afterwards. This is done to ensure the application state is wiped after each test.
These are the test scenarios:
- Gets only success data from the APIs
- Test that the calls don't throw exceptions if we don't provide all parameters in the request. 
- Some of the requests to the APIs are successful, others are not. In this case we check that we receive null values.
- We send enough data that it doesn't wait for the scheduler (all multiples of 5). In this case we verify that we receive a response immediately.
- Expect that we receive NULL-values in the response map when the corresponding call timed out.