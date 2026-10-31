package com.pdipatria.sensibo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Profiles;

@SpringBootApplication
public class SensiboTelemetryApplication {

	public static void main(String[] args) {
		var context = SpringApplication.run(SensiboTelemetryApplication.class, args);
		if (context.getEnvironment().acceptsProfiles(Profiles.of("import"))) {
			context.close();
		}
	}

}
