package com.henrymorgandibie.switchyard;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// TCP gateway disabled: this test doesn't exercise the network layer, and starting a real
// listening socket as a side effect of loading the context is unnecessary here.
@SpringBootTest
@TestPropertySource(properties = "switchyard.tcp.enabled=false")
class SwitchyardApplicationTests {

	@Test
	void contextLoads() {
	}

}
