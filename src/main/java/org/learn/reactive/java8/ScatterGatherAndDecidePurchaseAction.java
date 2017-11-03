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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ScatterGatherAndDecidePurchaseAction implements PurchaseAction<Order> {
	static private final AtomicInteger itemNumberGenerator = new AtomicInteger(0);

	private final String ORDER_SUCCESS_MSG = "Purchase order for %s executed successfully";
	private final String ORDER_FAILURE_MSG = "Purchase order for %s failed. Try again later";
	private final String ITEM_FAILURE_MSG  = "Line item %s could not be fulfilled due to error: %s %n";

	private final String FAILURE_SERVICE     = "https://requestb.in/1fyogcu1";
	private final String FULFILLMENT_SERVICE = "https://requestb.in/1n0s7o91";

	private final Set<LineItem> fulfilled = new HashSet();
	private final Set<LineItem> rejected  = new HashSet();

	@Override
	public void execute(Order order) throws Exception {
		CompletableFuture futureAction = new CompletableFuture();

		final List<CompletableFuture<Void>> futures = order
				.getItems()
				.stream()
				.map(li -> CompletableFuture
						.supplyAsync(notifyViaPrimaryPartnerService(li))
						.thenAcceptBothAsync(CompletableFuture.supplyAsync(notifyViaFallbackPartnerService(li)),
								fulfillOnAllSuccessfulResponse(li))
						.whenComplete(notifyOnError(li))
				).collect(Collectors.toList());

		//-- We wait until all async tasks have completed and then take the final action
		futureAction.allOf(futures.toArray(new CompletableFuture[futures.size()]))
				.whenComplete(notifyFulfillmentService(order))
				.join();

		System.out.printf("%s -- Order with id %s was executed %n",Thread.currentThread().getName(),order.getId());
	}

	private BiConsumer<Void,Throwable> notifyFulfillmentService(Order order) {
		BiConsumer<Void,Throwable> notifyAction = (none,error) -> {
			// Wrap up by notifying order status to an aggregation service
			System.out.printf("%s -- All line items for order %s have been processed %n",
					Thread.currentThread().getName(),
					order.getId());

			if(allItemsCanBeFulfilled(order)) {
				postMessageToEndpoint(FULFILLMENT_SERVICE,String.format(ORDER_SUCCESS_MSG,order.getId()));
				return;
			}

			//-- We have detected some failure which needs to be attended to
			postMessageToEndpoint(FULFILLMENT_SERVICE,String.format(ORDER_FAILURE_MSG,order.getId()));
		};
		return notifyAction;
	}

	private boolean allItemsCanBeFulfilled(Order order) {
		return fulfilled.size() == order.getItems().size();
	}

	//-- We want to keep track to individual line item failure so that they alone can be re-triggered
	private BiConsumer<Void,Throwable> notifyOnError(LineItem lineItem) {
		BiConsumer<Void,Throwable> finalAction = (none, throwable) -> {
			if(null != throwable) {
				String message = String.format(ITEM_FAILURE_MSG,lineItem.getItemId(),throwable.getMessage());
				System.out.println(message);
				postMessageToEndpoint(FAILURE_SERVICE,message);
				return;
			}
		};
		return finalAction;
	}

	private BiConsumer<String,String> fulfillOnAllSuccessfulResponse(LineItem lineItem) {
		return (r1,r2) -> {
			System.out.printf("%s -- checking fulfillment criteria...%n",Thread.currentThread().getName());
			if(r1.contains("ok") && r2.contains("ok")) {
				fulfilled.add(lineItem);
			} else {
				rejected.add(lineItem);
			}
		};
	}

	private Supplier<String> notifyViaPrimaryPartnerService(LineItem item) {
		final String endpoint = OrderHelper.getPrimaryServiceEndpoint(item);
		final String message  = item.getDescription() + "(" + item.getQuantity() + ")";
		return notifyPartnerService(endpoint,message);
	}

	private Supplier<String> notifyViaFallbackPartnerService(LineItem item) {
		final String endpoint = OrderHelper.getFallbackServiceEndpoint(item);
		final String message  = item.getDescription() + "(" + item.getQuantity() + ")";
		return notifyPartnerService(endpoint,message);
	}

	// -- For each line item, notify partner service so that they can track it separately
	private Supplier<String> notifyPartnerService(String endpoint, String request) {
		Supplier<String> responseSupplier = () -> {
			final Optional<String> potentialResponse = postMessageToEndpoint(endpoint, request);

			String result = "";
			itemNumberGenerator.incrementAndGet();

			if(potentialResponse.isPresent()) {
				result = potentialResponse.get();
			}
			return result;
		};
		return responseSupplier;
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
			System.err.printf("Failed to notify endpoint %s %n",endpoint);
			cause.printStackTrace();
		}
		System.out.printf("%s -- Endpoint %s responded with: %s %n",
				Thread.currentThread().getName(),
				endpoint,result.orElse("<empty>"));
		return result;
	}

	public static void main(String[] args) throws Exception {
		final PurchaseAction<Order> purchaseAction = new ScatterGatherAndDecidePurchaseAction();
		purchaseAction.execute(OrderHelper.generateOrder());
		System.out.printf("Number of items notified: %d %n",itemNumberGenerator.get());
	}
}
