package kafkareader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {KafkaAutoConfiguration.class})
@EnableAsync
public class KafkareaderApplication {

	public static void main(String[] args) {
		SpringApplication.run(KafkareaderApplication.class, args);
	}
}
