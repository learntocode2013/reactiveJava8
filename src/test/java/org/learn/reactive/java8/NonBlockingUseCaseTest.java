package org.learn.reactive.java8;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.learn.reactive.java8.ClientOperations.*;
import static org.learn.reactive.java8.FilterOperations.filterCartsByDate;
import static org.learn.reactive.java8.FilterOperations.filterCustomersByPurchases;

public class NonBlockingUseCaseTest {

	private final int poolSize = Runtime.getRuntime().availableProcessors();
	private final ScheduledExecutorService tPoolService = Executors
			.newScheduledThreadPool(poolSize);
	private final Logger logger = LoggerFactory.getLogger(NonBlockingUseCaseTest.class);

	//-- Compute intensive operation involving remote calls, processing and transformations --
	@Test
	@DisplayName("Offers discount for items in cart older than a week and value >= 1Lac")
	void offerDiscountForOrdersInCart() {
		final LocalDate endOfFY = LocalDate.of(2017, Month.MARCH, 31);
		final LocalDate maxAge = LocalDate.of(2017, Month.DECEMBER, 30);

		final CompletableFuture<Void> futureAction = CompletableFuture
				.supplyAsync(() -> OrderHelper.fetchAllShoppingCarts(endOfFY))
				.thenApply(filterCartsByDate(maxAge))
				.thenApply(filterCustomersByPurchases(1_000.00))
				.thenAccept(printEligibleCustomerCount());

		logger.info("{} thread is free to perform other computations",Thread.currentThread().getName());
		futureAction.join(); // Blocking call.Wait for the future to complete.
	}

	@Test
	@DisplayName("Fans out request to remote service expecting result within some time")
	void timeBoundOperation() {
		final CompletableFuture<String> timeoutFuture = new CompletableFuture<>();
		final CompletableFuture<String> futureResult = CompletableFuture.supplyAsync(() -> {
			try {
				Thread.sleep(TimeUnit.SECONDS.toMillis(3));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			return "200 OK";
		});

		tPoolService.schedule(() -> timeoutFuture.completeExceptionally(new RuntimeException("Operation timed out")),
				2,
				TimeUnit.SECONDS);

		futureResult
				.applyToEither(timeoutFuture,str -> str)
				.exceptionally(throwable -> {
					logger.error("Operation failed with error",throwable.getCause());
					return StringUtils.EMPTY;
				}).join();
		logger.info("Client thread [{}] exiting",Thread.currentThread().getName());
	}

	@Test
	@DisplayName("Fans out request to two mirror sites and consumes from the one that finishes first")
	void consumeResultFromFirstCompleted() throws Exception {
		//fetchResultFrom(TOP_STORIES_FROM_HN);
		final CompletableFuture<List<String>> futureFromHN = CompletableFuture
				.supplyAsync(() -> fetchResultAsync(TOP_STORIES_FROM_HN))
				.thenApply(potentialResponse -> getTopN(potentialResponse.get(), 3));

		final CompletableFuture<List<String>> futureFromDzone = CompletableFuture
				.supplyAsync(() -> fetchResultFrom(LATEST_FROM_DZONE))
				.thenApply(potentialResponse -> getTopN(potentialResponse.orElse(StringUtils.EMPTY), 3));

		CompletableFuture
				.anyOf(futureFromHN, futureFromDzone)
				.thenRun(() -> System.out.println("One of the computations completed"))
				.join(); //block until any one of the task completes

		// We do not know which future has completed
		Stream<String> resultStream = Stream.empty();

		if(futureFromHN.isDone()) {
			resultStream = futureFromHN.get().stream();
		} else if(futureFromDzone.isDone()){
			resultStream = futureFromDzone.get().stream();
		}

		// Non-blocking operation
		logger.info("------ Top {} results ------",3);
		resultStream.forEach(System.out::println);
		System.out.println();

		// For demonstration, wait for both futures to complete;
		// might block if the other futures are still being computed
		logger.info("--- Waiting for all futures to complete ---");
		final CompletableFuture<Void> allCompleted = CompletableFuture.allOf(futureFromHN, futureFromDzone);
		allCompleted
				.thenRun(() -> {
					logger.info("--- All futures done with computation ---");
					})
				.join();
	}


	@Test
	@DisplayName("Makes a remote service call and handles error if any")
	void handleErrors() throws ExecutionException, InterruptedException {
		final CompletableFuture<String> recoverableFuture = CompletableFuture
				.supplyAsync(() -> failsWithException())
				.exceptionally(Operations.logErrorAndRetryOperation())
				.thenApply(potentialResult -> potentialResult.get());

		recoverableFuture.join();
		logger.info("Value {} is available for client thread [{}]",
				recoverableFuture.get(),
				Thread.currentThread().getName());
	}

	private Consumer<Stream<Customer>> printEligibleCustomerCount() {
		return customers -> System.out.printf("%s -- %d customers are eligible for discounts %n",
				Thread.currentThread().getName(),
				customers.count());
	}
}
