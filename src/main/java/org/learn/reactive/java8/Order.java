package org.learn.reactive.java8;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class Order {
	private final UUID _id = UUID.randomUUID();
	private final LocalDateTime _purchaseDateTime;
	private final List<LineItem> _items;
	private final Customer _customer;

	public Order(LocalDateTime purchaseDateTime, List<LineItem> items, Customer customer) {
		this._purchaseDateTime = purchaseDateTime;
		this._items = items;
		this._customer = customer;
	}

	public UUID getId() {
		return _id;
	}

	public LocalDateTime getPurchaseDateTime() {
		return _purchaseDateTime;
	}

	public List<LineItem> getItems() {
		return _items;
	}

	public Customer getCustomer() {
		return _customer;
	}
}
