package org.learn.reactive.java8;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ScatterGatherPurchaseAction implements PurchaseAction<Order> {
	private static final AtomicInteger itemNumberGenerator = new AtomicInteger(0);
	private static final String LS = System.lineSeparator();
	private static final String ACTION_MSG = "Purchase order for %s executed successfully";
	private static final String ORDER_FAILED_MSG = "Order %s cannot be fulfilled now. Try again later";
	private static final String FULFILLMENT_SERVICE = "https://requestb.in/15086sx1";
	private static final String LINE_ITEM_FULFILMENT_TEMPLATE = "Order for %s(%d) was a %s - Tracking id %s";
	private static final Set<LineItem> fulfilled = new HashSet();
	private static final Set<LineItem> rejected = new HashSet();


	public void execute(Order order) throws ExecutionException, InterruptedException {

		// Scatter -- Fan out to external service(s)
		final List<CompletableFuture<Void>> futures = order.getItems()
				.stream()
				.map(li -> CompletableFuture
						.supplyAsync(notifyPartnerService(li))
						.thenAcceptAsync(processPartnerResponse(li))) //-- Consume the result in any available thread
				.collect(Collectors.toList());

		// Gather -- At some point we have to do something with all the responses
		boolean allDone;
		while(! (allDone = futures.stream().allMatch( future -> future.isDone() ))) {
			// loop endlessly...
		}
		

		if(allDone) {
			if(fulfilled.size() == order.getItems().size()) {
				postMessageToEndpoint(FULFILLMENT_SERVICE,String.format(ACTION_MSG,order.getId()));
				return;
			}
			// construct some meaningful message for the user...
			String message = constructOrderStatusMessage(order);
			postMessageToEndpoint(FULFILLMENT_SERVICE,message);
		}

	}

	private String constructOrderStatusMessage(Order order) {

		if(fulfilled.isEmpty()) {
			return String.format(ORDER_FAILED_MSG,order.getId().toString());
		}

		order.getItems()
				.stream()
				.filter( li -> !fulfilled.contains(li) && !rejected.contains(li))
				.forEach(li -> rejected.add(li));

		final String failedLineItemMsg = rejected.stream()
				.map(li -> String.format(LINE_ITEM_FULFILMENT_TEMPLATE,
						li.getDescription(),
						li.getQuantity(),
						"failure",
						li.getItemId())
				).collect(Collectors.joining(LS));

		final String fulfiledLineItemMsg = fulfilled.stream()
				.map(li -> String.format(LINE_ITEM_FULFILMENT_TEMPLATE,
						li.getDescription(),
						li.getQuantity(),
						"success",
						li.getItemId())
				).collect(Collectors.joining(LS));

		StringBuffer msgBuffer = new StringBuffer();
		msgBuffer.append("Order details for " + order.getId())
				 .append(LS)
				 .append(LS)
				 .append("--- Fulfilled order count(" + fulfilled.size()+") ---")
				 .append(LS)
				 .append(fulfiledLineItemMsg)
				 .append(LS)
				 .append(LS)
				 .append("--- Rejected order count(" + rejected.size()+") ---")
				 .append(LS)
				 .append(failedLineItemMsg);

		return msgBuffer.toString();
	}

	// -- For each line item, notify partner service so that they can track it separately
	private Supplier<String> notifyPartnerService(LineItem item) {
		final String endpoint = OrderHelper.getPrimaryServiceEndpoint(item);
		Supplier<String> responseSupplier = () -> {
			final Optional<String> potentialResponse = postMessageToEndpoint(endpoint,
					item.getDescription() + "(" + item.getQuantity() + ")");

			String result = "";
			itemNumberGenerator.incrementAndGet();

			if(potentialResponse.isPresent()) {
				result = potentialResponse.get();
				System.out.printf("%s -- Received partner response %s for line item %s %n",
						Thread.currentThread().getName(),
						result,
						item.getItemId());
			}
			return result;
		};
		return responseSupplier;
	}

	private Consumer<String> processPartnerResponse(LineItem lineItem) {
		return (String response) -> {
			System.out.printf("%s -- processing partner response for line item %s %n",
					Thread.currentThread().getName(),
					lineItem.getItemId());
			if(response.contains("ok")) {
				fulfilled.add(lineItem);
				return;
			}
			rejected.add(lineItem);
		};
	}

	private void notifyCustomerService(String message) {
		postMessageToEndpoint(FULFILLMENT_SERVICE,message);
	}

	// -- This is a blocking network call. Thread will not be released until the
	// calls returns successfully or with an error
	private Optional<String> postMessageToEndpoint(String endpoint, String message) {

		if(null == endpoint || null == message) return Optional.empty();

		Optional<String> result = Optional.empty();

		Client client = ClientBuilder.newClient();
		WebTarget webTarget = client.target(endpoint);
		try {
			final Response response = webTarget
					.request(MediaType.TEXT_PLAIN)
					.post(Entity.entity(message,MediaType.TEXT_PLAIN));
			result = Optional.of(response.readEntity(String.class));
		} catch (Exception cause) {
			System.err.printf("Failed to notify endpoint %s %n" + endpoint);
			cause.printStackTrace();
		}
		System.out.printf("%s -- Endpoint %s responded with: %s %n",
				Thread.currentThread().getName(),
				endpoint,result.get());
		return result;
	}


	public static void main(String[] args) throws Exception {
		PurchaseAction<Order> purchaseAction = new ScatterGatherPurchaseAction();
		purchaseAction.execute(OrderHelper.generateOrder());
		System.out.printf("Number of items notified: %d %n",itemNumberGenerator.get());
	}
}
