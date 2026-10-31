package com.pdipatria.sensibo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import com.pdipatria.sensibo.client.SensiboClient;
import com.pdipatria.sensibo.service.OneShotImport;
import com.pdipatria.sensibo.service.ScheduledPolling;
import com.pdipatria.sensibo.web.PollingController;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SensiboTelemetryApplicationTests extends PostgresIntegrationTest {
	@Autowired ApplicationContext context;

	@Test
	void contextLoads() {
		assertTrue(context.getBeansOfType(SensiboClient.class).isEmpty());
		assertTrue(context.getBeansOfType(OneShotImport.class).isEmpty());
		assertTrue(context.getBeansOfType(ScheduledPolling.class).isEmpty());
		assertTrue(context.getBeansOfType(PollingController.class).isEmpty());
	}

}
