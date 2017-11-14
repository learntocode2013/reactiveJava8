package org.learn.reactive.java8;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.learn.reactive.java8.BackupOperations.*;
import static org.learn.reactive.java8.ClientOperations.*;
import static org.learn.reactive.java8.FilterOperations.filterCartsByDate;
import static org.learn.reactive.java8.FilterOperations.filterCustomersByPurchases;

public class NonBlockingUseCaseTest {

	private final int poolSize = Runtime.getRuntime().availableProcessors();
	private final ScheduledExecutorService tPoolService = Executors
			.newScheduledThreadPool(poolSize);
	private final ThreadFactory diskThreadFactory = new ThreadFactoryBuilder()
			.setNameFormat("Disk-Worker-%d")
			.build();
	private final ThreadFactory networkThreadFactory = new ThreadFactoryBuilder()
			.setNameFormat("Network-Worker-%d")
			.build();

	private final ExecutorService DISK_READ_THREAD_POOL = Executors.newSingleThreadExecutor(diskThreadFactory);
	private final ExecutorService NETWORK_WRITE_THREAD_POOL = Executors.newSingleThreadExecutor(networkThreadFactory);
	private final Path DISK_FILE_PATH = Paths.get("/","Users","disen","speakers.json");

	private final BlockingQueue boundedTaskQueue = new ArrayBlockingQueue(poolSize * 3 );
	private final ThreadPoolExecutor DISK_IO_THREAD_POOL = new ThreadPoolExecutor(poolSize,2*poolSize,
			10,TimeUnit.SECONDS, boundedTaskQueue);
	static private BackupTask backupTask = null;

	private final Logger logger = LoggerFactory.getLogger(NonBlockingUseCaseTest.class);

	@BeforeAll
	static void beforeAll() {
		backupTask = new BackupTaskBuilder()
				.withFileCount(10)
				.withDirCount(10)
				.withFilePattern("data")
				.withTableCount(10)
				.build();
	}

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

	@Test
	@DisplayName("Performs disk read and network write by utilizing a combination of built-in common pool and other thread pool")
	void performIOIntensiveTask() {
		final CompletableFuture<Optional<String>> potentialResult = CompletableFuture
				.supplyAsync(() -> readFromDiskBlocking(DISK_FILE_PATH), DISK_READ_THREAD_POOL)
				.exceptionally(throwable -> {
					logger.error("Reading {} from disk failed", throwable.getCause());
					return Optional.empty();
				})
				.thenApplyAsync(potentialContent -> {
					logger.info("Thread {} performing transformation on content {} from disk",
							Thread.currentThread().getName(),
							potentialContent);
					if (potentialContent.isPresent()) {
						return potentialContent.get().stream().collect(joining(System.lineSeparator()));
					}
					return StringUtils.EMPTY;
				})
				.thenApplyAsync(content -> fetchResultFrom(TOP_STORIES_FROM_HN), NETWORK_WRITE_THREAD_POOL)
				.whenComplete((potentialResp, throwable) -> { if( null == throwable) {
					logger.info("Number of top stories from HN call is:  {}",potentialResp.get().length());
				}});


		logger.info("Client thread - {} free to process other operations",Thread.currentThread().getName());
		potentialResult.join();
	}

	@Test
	@DisplayName("Demonstrates how different task types can be assigned to different thread pools for execution")
	void offloadTasksToDifferentPools() {
		// Backup all single files
		final List<CompletableFuture<Void>> potentialFileBackup = backupFiles();

		final CompletableFuture<Void> futureLogger = CompletableFuture.runAsync(() -> {
			while(DISK_IO_THREAD_POOL.getActiveCount() != 0) {
				logger.info("{} tasks are scheduled for disk i/o operation", DISK_IO_THREAD_POOL.getTaskCount());
				logger.info("{} threads are performing disk i/o", DISK_IO_THREAD_POOL.getActiveCount());
				addLatency(4);
			}
		});

		// Backup all directories
		final List<CompletableFuture<Void>> potentialDirBackup = backupFolders();

		// Backup all db tables
		final List<CompletableFuture<Void>> potentialDbBackup = backupDBTables();

		// Zip contents of staging directory to generate final artifact
		awaitFutureCompletion(potentialFileBackup, potentialDirBackup, potentialDbBackup);
		futureLogger.complete(null);
		logger.info("All futures completed. Backup Artifact {} can now be generated",
				backupTask.getTargetArtifactLocation().toString());
	}

	private void awaitFutureCompletion(List<CompletableFuture<Void>> potentialFileBackup,
	                                   List<CompletableFuture<Void>> potentialDirBackup,
	                                   List<CompletableFuture<Void>> potentialDbBackup) {
		potentialFileBackup.addAll(potentialDirBackup);
		potentialFileBackup.addAll(potentialDbBackup);
		final CompletableFuture[] futures = potentialFileBackup.toArray(new CompletableFuture[potentialFileBackup.size()]);
		CompletableFuture.allOf(futures).join();
	}

	private List<CompletableFuture<Void>> backupDBTables() {
		return backupTask.getSourceTables()
				.map(tableName -> CompletableFuture
						.supplyAsync(exportRowsFromTable(tableName), NETWORK_WRITE_THREAD_POOL)
						.thenApplyAsync(saveToFile(backupTask.getStagingDirLocation().getParent().resolve(tableName)),
								DISK_IO_THREAD_POOL)
						.thenAcceptAsync(moveToStagingDir(backupTask.getStagingDirLocation())))
				.collect(toList());
	}

	private List<CompletableFuture<Void>> backupFolders() {
		return backupTask.getSourceDirLocation()
				.map(dir -> CompletableFuture
						.supplyAsync(compress(dir), DISK_IO_THREAD_POOL)
						.thenAcceptAsync(moveToStagingDir(backupTask.getStagingDirLocation())))
				.collect(toList());
	}

	/**
	 * 1) We have a separate thread pool for disk i/o <br>
	 * 2) Only light weight tasks, like moving files from one place to another <br>
	 * is assigned to the default ForkJoin common pool of CF
	 */
	private List<CompletableFuture<Void>> backupFiles() {
		return backupTask.getSourceFileLocation()
					.map(file -> CompletableFuture
							.supplyAsync(compress(file), DISK_IO_THREAD_POOL)
							.thenAcceptAsync(moveToStagingDir(backupTask.getStagingDirLocation())))
					.collect(toList());
	}

	private Consumer<Stream<Customer>> printEligibleCustomerCount() {
		return customers -> System.out.printf("%s -- %d customers are eligible for discounts %n",
				Thread.currentThread().getName(),
				customers.count());
	}
}
