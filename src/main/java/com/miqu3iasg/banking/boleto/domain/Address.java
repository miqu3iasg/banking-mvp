package com.miqu3iasg.banking.boleto.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address {

	@Column(name = "street", nullable = false, length = 200)
	private String street;

	@Column(name = "number", nullable = false, length = 20)
	private String number;

	@Column(name = "neighborhood", nullable = false, length = 100)
	private String neighborhood;

	@Column(name = "zipcode", nullable = false, length = 8)
	private String zipcode;

	@Column(name = "city", nullable = false, length = 100)
	private String city;

	@Column(name = "state", nullable = false, length = 2)
	private String state;

	public static Address of (
		String street,
		String number,
		String neighborhood,
		String zipcode,
		String city,
		String state
	) {
		if (street == null || street.isBlank())
			throw new IllegalArgumentException("street is required");
		if (number == null || number.isBlank())
			throw new IllegalArgumentException("number is required");
		if (neighborhood == null || neighborhood.isBlank())
			throw new IllegalArgumentException("neighborhood is required");
		if (zipcode == null || zipcode.isBlank())
			throw new IllegalArgumentException("zipcode is required");
		if (city == null || city.isBlank()) throw new IllegalArgumentException("city is required");
		if (state == null || state.isBlank()) throw new IllegalArgumentException("state is required");

		var address = new Address();
		address.street = street;
		address.number = number;
		address.neighborhood = neighborhood;
		address.zipcode = zipcode.replaceAll("[^0-9]", "");
		address.city = city;
		address.state = state;
		return address;
	}
}
