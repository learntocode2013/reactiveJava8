package org.learn.reactive.java8;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class ShoppingCart {
	private final UUID _cartId;
	private final Customer _orderedBy;
	private final List<LineItem> _items;
	private final LocalDateTime _createdOn;

	public ShoppingCart(Customer orderedBy, List<LineItem> items, LocalDateTime createdOn) {
		this._orderedBy = orderedBy;
		this._items = items;
		this._createdOn = createdOn;
		_cartId = UUID.randomUUID();
	}

	public Customer getOrderedBy() {
		return _orderedBy;
	}

	public List<LineItem> getItems() {
		return _items;
	}

	public LocalDateTime getCreationDateTime() {
		return _createdOn;
	}

	public UUID getId() {
		return _cartId;
	}
}
