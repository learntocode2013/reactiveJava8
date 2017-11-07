package org.learn.reactive.java8;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ClientOperations {
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
			final Response response = webTarget
					.request(MediaType.TEXT_PLAIN)
					.get();
			result = Optional.of(response.readEntity(String.class));
		}
		catch (Exception cause)
		{
			System.err.printf("Failed to notify endpoint %s %n",endpoint);
			cause.printStackTrace();
		}
		//-- uncomment for debugging
		/*System.out.printf("%s -- Endpoint %s responded with: %s %n",
				Thread.currentThread().getName(),
				endpoint,result.orElse("<empty>"));*/
		return result;
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
				.map(id -> fetchResultFrom(String.format(FIRST_TOP_STORY_HN,id)))
				.map(or -> Arrays.asList(
						or.get().split(System.lineSeparator()))
						.stream()
						.filter(line -> line.contains("url")).findFirst())
				.filter(ol -> ol.isPresent())
				.map(ol -> ol.get().split(": ")[1].replaceAll("\"",""))
				.collect(Collectors.toSet());
		return links.stream().limit(topN).collect(Collectors.toList());
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
