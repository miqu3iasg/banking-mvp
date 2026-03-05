package com.miqu3iasg.banking.compliance.api.dto;

import java.util.List;

public record CnpjResponse(
	String cnpj,
	String branchType,
	String legalName,
	String tradeName,
	String registrationStatus,
	String registrationStatusDate,
	String registrationStatusReason,
	Integer legalNatureCode,
	String legalNature,
	String activityStartDate,
	Long primaryActivityCode,
	String primaryActivityDescription,
	List<SecondaryActivity> secondaryActivities,
	Address address,
	String phone1,
	String phone2,
	String fax,
	Double shareCapital,
	String companySize,
	Boolean simplesNacional,
	Boolean mei,
	String email,
	List<Partner> partners
) {
	public record SecondaryActivity(Long code, String description) { }

	public record Address(
		String street,
		String number,
		String complement,
		String neighborhood,
		String zipCode,
		String state,
		String city,
		Integer ibgeCityCode
	) { }

	public record Partner(
		Integer partnerType,
		String name,
		String taxId,
		Integer qualificationCode,
		String qualification,
		String joinDate,
		String ageRange,
		String legalRepresentativeName,
		String legalRepresentativeTaxId
	) { }
}
