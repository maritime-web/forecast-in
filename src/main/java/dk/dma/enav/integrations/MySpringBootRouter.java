package dk.dma.enav.integrations;

import org.apache.camel.spring.boot.FatJarRouter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class MySpringBootRouter extends FatJarRouter {

    @Override
    public void configure() {
        from("timer://trigger").
                transform().simple("ref:myBean").
                to("log:out", "mock:test");
    }

    @Bean
    String myBean() {
        return "I'm Spring bean!";
    }

}
