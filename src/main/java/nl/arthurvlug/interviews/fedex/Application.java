package nl.arthurvlug.interviews.fedex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        startApplication(args);
    }

    public static ConfigurableApplicationContext startApplication(final String[] args) {
        return SpringApplication.run(Application.class, args);
    }
}