package org.learn.reactive.java8;

import java.util.UUID;

public class LineItem {
	private final String _itemId;

	public String getItemId() {
		return _itemId;
	}

	private final String _description;
	private final String _fulfilledBy;
	private final double _value;
	private final int _quantity;

	public LineItem(String description, String fulfilledBy, double value, int quantity) {
		this._itemId = UUID.randomUUID().toString();
		this._description = description;
		this._fulfilledBy = fulfilledBy;
		this._value = value;
		this._quantity = quantity;
	}

	public int getQuantity() {
		return _quantity;
	}

	public String getDescription() {

		return _description;
	}

	public String getFulfilledBy() {
		return _fulfilledBy;
	}

	public double getValue() {
		return _value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		LineItem lineItem = (LineItem) o;

		return _itemId.equals(lineItem._itemId);
	}

	@Override
	public int hashCode() {
		return _itemId.hashCode();
	}
}
