package org.learn.reactive.java8;

import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class OrderHelper {
	static private final String PRIMARY_VENDOR_SERVICE = "https://requestb.in/sfqjfysf";
	static private final String FALLBACK_VENDOR_SERVICE = "https://requestb.in/159btae1";
	static private final String INVALID_VENDOR_SERVICE = "https://requestbC.in/151hzga1";
	static private final String[] itemTypes = {
			"Computer accessory",
			"Smartphone",
			"apparels",
			"beauty",
			"food"
	} ;
	static private final String[] vendors = {
			"Ezone",
			"MobileWorld",
			"Shoppers stop",
			"Patanjali"
	};
	static private final double[] values = {
			27002.34,
			56000,
			863.123,1563.22,
			901.23
	};

	static private final Map<String,String> primaryEndpointByVendor = new HashMap() {{
		put("Ezone", PRIMARY_VENDOR_SERVICE);
		put("MobileWorld", PRIMARY_VENDOR_SERVICE);
		put("Shoppers stop", PRIMARY_VENDOR_SERVICE);
		put("Patanjali", PRIMARY_VENDOR_SERVICE);
	}};

	static private final Map<String,String> fallbackEndpointByVendor = new HashMap() {{
		put("Ezone", INVALID_VENDOR_SERVICE);
		put("MobileWorld", INVALID_VENDOR_SERVICE);
		put("Shoppers stop", INVALID_VENDOR_SERVICE);
		put("Patanjali", INVALID_VENDOR_SERVICE);
	}};

	static private final Random vendorIndex = new Random(vendors.length - 1);
	static private final Random typeIndex = new Random(itemTypes.length - 1);
	static private final Random valueIndex = new Random(values.length - 1);

	public static List<LineItem> generateLineItems(int count) {
		return IntStream.rangeClosed(1, count)
				.mapToObj(copies -> createItem(copies))
				.collect(Collectors.toList());
	}

	public static Order generateOrder() {
		return new Order(LocalDateTime.now(),generateLineItems(3),createCustomer(null,null));
	}

	public static ShoppingCart createCart(Customer orderedBy) {
		int month = new Random().nextInt(12); month = month == 0 ? 1 : month;
		return new ShoppingCart(
				orderedBy,
				generateLineItems(1),
				LocalDateTime.of(LocalDate.of(2017, Month.of(month),1), LocalTime.now()));
	}

	public static Stream<ShoppingCart> fetchAllShoppingCarts(LocalDate dateAfter) {
		//-- Simulate some latency by increasing the cart volume
		return createCarts(1_00)
				.filter(cart -> cart.getCreationDateTime().toLocalDate().isAfter(dateAfter));
	}

	public static Stream<ShoppingCart> createCarts(int count) {
		return createCustomers(count).map(customer -> createCart(customer));
	}

	public static String getPrimaryServiceEndpoint(LineItem item) {
		return primaryEndpointByVendor.get(item.getFulfilledBy());
	}

	public static String getFallbackServiceEndpoint(LineItem item) {
		return fallbackEndpointByVendor.get(item.getFulfilledBy());
	}

	private static Customer createCustomer(String firstName, String lastName) {
		return new Customer(
				StringUtils.isEmpty(firstName) ? "Dibakar" : firstName,
				StringUtils.isEmpty(lastName) ? "Sen" : lastName,
				"1st, Developer Street",
				"91-999-999-9999");
	}

	private static Stream<Customer> createCustomers(int count) {
		return IntStream.rangeClosed(1,count)
				.mapToObj(num -> createCustomer("John-" + num, "Wick-" + num));
	}

	private static LineItem createItem(int quantity) {
		return new LineItem(
				itemTypes[typeIndex.nextInt(itemTypes.length-1)],
				vendors[vendorIndex.nextInt(vendors.length-1)],
				values[valueIndex.nextInt(values.length-1)],
				quantity);
	}
}
