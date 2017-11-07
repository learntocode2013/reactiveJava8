package org.learn.reactive.java8;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.learn.reactive.java8.ClientOperations.*;
import static org.learn.reactive.java8.FilterOperations.filterCartsByDate;
import static org.learn.reactive.java8.FilterOperations.filterCustomersByPurchases;

public class NonBlockingUseCaseTest {

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

		System.out.printf("%s -- is free to perform other computations... %n",Thread.currentThread().getName());
		futureAction.join(); // Blocking call.Wait for the future to complete.
	}

	@Test
	@DisplayName("Fans out request to two mirror sites and consumes from the one that finishes first")
	void consumeResultFromFirstCompleted() throws ExecutionException, InterruptedException {

		final CompletableFuture<List<String>> futureFromHN = CompletableFuture
				.supplyAsync(() -> fetchResultFrom(TOP_STORIES_FROM_HN))
				.thenApply(potentialResponse -> getTopN(potentialResponse.get(), 3));

		final CompletableFuture<List<String>> futureFromDzone = CompletableFuture
				.supplyAsync(() -> fetchResultFrom(LATEST_FROM_DZONE))
				.thenApply(potentialResponse -> getTopN(potentialResponse.get(), 3));

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
		System.out.println("------ Top 3 results ------");
		resultStream.forEach(System.out::println);
		System.out.println();

		// For demonstration, wait for both futures to complete;
		// might block if the other futures are still being computed
		System.out.println("--- Waiting for all futures to complete ---");
		CompletableFuture
				.allOf(futureFromHN,futureFromDzone).thenRun(() -> {
					System.out.println("--- All futures done with computation ---");
					})
				.join();
	}

	private Consumer<Stream<Customer>> printEligibleCustomerCount() {
		return customers -> System.out.printf("%s -- %d customers are eligible for discounts %n",
				Thread.currentThread().getName(),
				customers.count());
	}
}
