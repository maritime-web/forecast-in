package dk.dma.enav.integrations;

import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import java.net.ConnectException;

public class ErrorHandlingTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                from("file:target/test-classes?consumer.bridgeErrorHandler=true&noop=true")
                        .to("mock:output");
            }
        };
    }

    @Test
    public void testError() throws Exception {
        template.setDefaultEndpointUri("mock:output");
        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                // intercept the delivery of messages and throw an exception
                interceptSendToEndpoint("mock:output")
                        .process(exchange -> {
                            throw new ConnectException("simulated connection error");
                        });
            }
        });
        // get the mock endpoints
        MockEndpoint end = context.getEndpoint("mock:output", MockEndpoint.class);
        MockEndpoint dead = context.getEndpoint("mock:dead", MockEndpoint.class);

        end.expectedMessageCount(0);
        dead.expectedMinimumMessageCount(1);
        template.sendBodyAndHeader("file://target/test-classes", Exchange.FILE_NAME, "DMI1.nc");

        end.assertIsSatisfied();
        dead.assertIsSatisfied();

        // get the thrown exception and assert that it is correct
        Exception cause = dead.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);

        assertNotNull(cause);
        assertEquals("simulated connection error", cause.getMessage());
    }
}
