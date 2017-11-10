package org.learn.reactive.java8;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ClientOperations {
	private static final Logger logger = LoggerFactory.getLogger(ClientOperations.class);
	public static final String TOP_STORIES_FROM_HN = "https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty";
	public static final String FIRST_TOP_STORY_HN = "https://hacker-news.firebaseio.com/v0/item/%s.json?print=pretty";
	public static final String LATEST_FROM_DZONE = "https://dzone.com/java-jdk-development-tutorials-tools-news";
	public static final String SOME_INVALID_DOMAIN = "http://-domain.com/noresponse.html";

	// -- This is a blocking network call. Thread will not be released until the
	// calls returns successfully or with an error
	static public Optional<String> fetchResultFrom(String endpoint)
	{
		if(null == endpoint) return Optional.empty();

		Optional<String> result = Optional.empty();

		Client client = ClientBuilder.newClient();
		WebTarget webTarget = client.target(endpoint);
		try
		{
			logger.info("Thread {} attempting to read from {}",Thread.currentThread().getName(),endpoint);
			final Response response = webTarget
					.request(MediaType.TEXT_PLAIN)
					.get();
			result = Optional.of(response.readEntity(String.class));
		}
		catch (Exception cause)
		{
			logger.error("Could not reach endpoint {}",endpoint,cause.getCause());
			cause.printStackTrace();
		}
		//-- uncomment for debugging
		/*System.out.printf("%s -- Endpoint %s responded with: %s %n",
				Thread.currentThread().getName(),
				endpoint,result.orElse("<empty>"));*/
		return result;
	}

	static public Optional<String> fetchResultAsync(String endpoint)
	{
		final AsyncHttpClient asyncHttpClient = new DefaultAsyncHttpClient();
		final CompletableFuture<org.asynchttpclient.Response> futureResponse =
				asyncHttpClient.prepareGet(endpoint)
						.execute()
						.toCompletableFuture()
						.exceptionally(logAndReturnNothing(endpoint));


		try
		{
			//-- This is a blocking call since we are extracting the value eagerly
			return futureResponse.thenApply(extractContentFromResponse(endpoint)).get();
		}
		catch (Exception ignore)
		{
			logger.error("Returning empty result as fallback due to error",ignore.getCause());
			return Optional.empty();
		}
	}

	static private Function<org.asynchttpclient.Response,Optional<String>> extractContentFromResponse(String endpoint)
	{
		return response -> {
			// The response might have been set to null due to an error in an upstream operator..
			if(null == response) { return Optional.empty() ; }

			final String responseBody = response.getResponseBody(Charset.defaultCharset());
			logger.info("Response from {} is {}",endpoint,responseBody);
			return Optional.of(responseBody);
		};
	}
	static private Function<Throwable,org.asynchttpclient.Response> logAndReturnNothing(String endpoint)
	{
		return throwable -> {
			logger.info("Remote call to {} failed !!!",endpoint,throwable);
			return null;
		};
	}

	static public Optional<String> failsWithException()
	{
		try
		{
			Thread.sleep(TimeUnit.SECONDS.toMillis(4));
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		throw new RuntimeException("Computation was aborted due to resource limitations !!!");
	}

	public static List<String> getTopN(String response, int topN) {
		if(response.contains("dzone")) { return parseDzoneOutput(response,topN); }
		return parseHNOutput(response,topN);
	}

	private static List<String> parseDzoneOutput(String result, int topN) {
		System.out.printf("%s -- parsing Dzone response to return top %d result(s) %n",
				Thread.currentThread().getName(),
				topN);
		final Set<String> links = Arrays.asList(result.split(System.lineSeparator()))
				.stream()
				.filter(line -> line.contains("/articles"))
				.map(line -> line.substring(line.indexOf("/articles")))
				.map(line -> line.substring(0, line.indexOf("\"")))
				.map(relativeUrl -> "https://dzone.com" + relativeUrl)
				.collect(Collectors.toSet());

		return links.stream().limit(topN).collect(Collectors.toList());
	}

	private static List<String> parseHNOutput(String response, int topN) {
		System.out.printf("%s -- parsing HN response to return top %d result(s) %n",
				Thread.currentThread().getName(),
				topN);
		final String result = response
				.replaceAll("\\[","")
				.replaceAll("]","")
				.replaceAll(", ",",")
				.trim();

		final Set<String> links = Arrays.asList(result.split(","))
				.stream()
				.parallel()
				.map(id -> fetchResultAsync(String.format(FIRST_TOP_STORY_HN,id)))
				.map(or -> Arrays.asList(
						or.get().split(System.lineSeparator()))
						.stream()
						.filter(line -> line.contains("url")).findFirst())
				.filter(ol -> ol.isPresent())
				.map(ol -> ol.get().split(": ")[1].replaceAll("\"",""))
				.collect(Collectors.toSet());
		return links.stream().limit(topN).collect(Collectors.toList());
	}

	public static Optional<List<String>> readFromDiskBlocking(Path filePath)
	{
		try
		{
			logger.info("Thread {} reading file {} from disk",
					Thread.currentThread().getName(),
					filePath.toString());

			final List<String> lines = Files.readAllLines(filePath, Charset.defaultCharset());
			return Optional.of(lines);
		}
		catch (IOException cause)
		{
			logger.error("Failed to read {} from disk", cause.getCause());
		}
		return Optional.empty();
	}

	public static void main(String[] args)
	{
		final Optional<String> optResult = ClientOperations
				.fetchResultFrom(TOP_STORIES_FROM_HN);
		final String result = optResult.get()
				.replaceAll("\\[","")
				.replaceAll("]","")
				.replaceAll(", ",",")
				.trim();
		System.out.println(Arrays.asList(result.split(",")).toString());
		Arrays.asList(result.split(","))
				.stream()
				.parallel()
				.map(id -> fetchResultFrom(String.format(FIRST_TOP_STORY_HN,id)))
				//.peek(r -> System.out.println(r))
				.map(or -> Arrays.asList(
						or.get().split(System.lineSeparator()))
						.stream()
						.filter(line -> line.contains("url")).findFirst())
				.filter(ol -> ol.isPresent())
				.map(ol -> ol.get().split(": ")[1].replaceAll("\"",""))
				.collect(Collectors.toList());

	}
}
