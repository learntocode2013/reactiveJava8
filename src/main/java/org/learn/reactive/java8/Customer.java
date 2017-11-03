package org.learn.reactive.java8;

public class Customer {
	private final String _firstName;
	private final String _lastName;
	private final String _shippingAddress;
	private final String _contactNumber;

	public Customer(String firstName, String lastName,
	                String shippingAddress, String contactNumber) {
		this._firstName = firstName;
		this._lastName = lastName;
		this._shippingAddress = shippingAddress;
		this._contactNumber = contactNumber;
	}

	public String getFirstName() {
		return _firstName;
	}

	public String getLastName() {
		return _lastName;
	}

	public String getShippingAddress() {
		return _shippingAddress;
	}

	public String getContactNumber() {
		return _contactNumber;
	}
}
