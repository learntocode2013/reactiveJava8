package org.learn.reactive.java8;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Operations
{
	static private final Logger logger = LoggerFactory.getLogger(Operations.class);
	static private final String FALLBACK_VALUE = "NA";

	public static BiFunction<Optional<String>,Throwable,String> logAndReturnFallbackValueOnError()
	{
		return (potentialResult, exception) -> {
			if (exception != null) {
				// We want to unwrap the original exception cause
				logger.error("{} caught exception",Thread.currentThread().getName(),exception.getCause());
				// The Exception is expected and we return a fallback value
				return FALLBACK_VALUE;
			}
			return potentialResult.get();
		};
	}

	public static Function<String,String> retryOperation()
	{
		return result -> {
			if(result.equals("NA")) {
				//retry again
				logger.info("{} retrying failed operation",Thread.currentThread().getName());
				return "<empty>";
			}
			logger.info("{} got result {}",Thread.currentThread().getName(),result);
			return result;
		};
	}

	public static Function<Throwable,Optional<String>> logErrorAndRetryOperation()
	{
		return throwable -> {
			logger.error("Operation failed in first attempt", throwable);
			return performFallbackOperation();
		};
	}

	public static Optional<String> performFallbackOperation()
	{
		try
		{
			Thread.sleep(TimeUnit.SECONDS.toMillis(3));
		}
		catch (InterruptedException ignore)
		{
		}
		return Optional.of("some_value");
	}
}
