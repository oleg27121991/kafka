package kafkareader;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import kafkareader.config.TestKafkaConfig;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = TestKafkaConfig.class)
class KafkareaderApplicationTests {

	@Test
	void contextLoads() {
		// Проверяем, что контекст приложения загружается
	}

}
