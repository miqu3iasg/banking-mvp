package com.miqu3iasg.banking.shared.config;

public interface EfiProperties {
	String baseUrl ();

	int responseTimeoutInSeconds ();

	String certificatePassword ();

	String certificatePath ();
}
