package org.learn.reactive.java8;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

	public static Order generateOrder() {
		final List<LineItem> items = IntStream.range(1, 10)
				.mapToObj(quant -> createItem(quant))
				.collect(Collectors.toList());

		return new Order(LocalDateTime.now(),items,createCustomer());
	}

	public static String getPrimaryServiceEndpoint(LineItem item) {
		return primaryEndpointByVendor.get(item.getFulfilledBy());
	}

	public static String getFallbackServiceEndpoint(LineItem item) {
		return fallbackEndpointByVendor.get(item.getFulfilledBy());
	}

	private static Customer createCustomer() {
		return new Customer(
				"Dibakar",
				"Sen",
				"1st, Developer Street",
				"91-999-999-9999");
	}

	private static LineItem createItem(int quantity) {
		return new LineItem(
				itemTypes[typeIndex.nextInt(itemTypes.length-1)],
				vendors[vendorIndex.nextInt(vendors.length-1)],
				values[valueIndex.nextInt(values.length-1)],
				quantity);
	}
}
