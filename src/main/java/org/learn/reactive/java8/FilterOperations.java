package org.learn.reactive.java8;

import java.time.LocalDate;
import java.util.function.Function;
import java.util.stream.Stream;

public class FilterOperations {
	public static Function<Stream<ShoppingCart>,Stream<ShoppingCart>> filterCartsByDate(LocalDate date) {
		return carts -> {
			System.out.printf("%s -- started filtering cart by date %s %n",
					Thread.currentThread().getName(),
					date.toString());
			return carts.filter(cart -> cart.getCreationDateTime().toLocalDate().isAfter(date));
		};
	}

	public static Function<Stream<ShoppingCart>,Stream<Customer>> filterCustomersByPurchases(double value) {
		Function<Stream<ShoppingCart>,Stream<Customer>> customersByValue = carts -> {
			System.out.printf("%s -- started filtering cart by purchase value %f %n",
					Thread.currentThread().getName(),
					value);
			return carts
					.filter(cart -> cart.getItems().stream().mapToDouble(item -> item.getValue()).sum() >= value)
					.map(cart -> cart.getOrderedBy());
		};

		return customersByValue;
	}
}
