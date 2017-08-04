package dk.dma.enav.integrations;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileFilter;
import org.apache.camel.spring.boot.FatJarRouter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class ForecastInRouter extends FatJarRouter {

    // standard template for the routes
    private static String routeTemplate = "ftp://%s@%s%s?password=%s&passiveMode=true" +
            "&filter=#notTooOld&localWorkDirectory=/tmp&idempotent=true&consumer.bridgeErrorHandler=true&binary=true&delay=15m";

    // where the dmi consumer should consume from
    private String dmiFTP = String.format(routeTemplate, "{{dmi.user}}", "{{dmi.server}}", "{{dmi.directory}}", "{{dmi.password}}");

    // where the fcoo consumer should consume from
    private String fcooFTP = String.format(routeTemplate, "{{fcoo.user}}", "{{fcoo.server}}", "{{fcoo.directory}}", "{{fcoo.password}}");

    @Override
    public void configure() {
        // options for tracing and debugging
        this.getContext().setTracing(true);
        this.onException(Exception.class)
                .maximumRedeliveries(6)
                .process(exchange -> log.error("Exchange failed for: " + exchange.getIn().getHeader(Exchange.FILE_NAME_ONLY)));

        // create the dmi route
        from(dmiFTP)
                .routeId("dmiRoute")
                .to("file://{{dmi.download.directory}}?fileExist=Ignore&chmod=666&chmodDirectory=666");

        // create the fcoo route
        from(fcooFTP)
                .routeId("fcooRoute")
                .to("file://{{fcoo.download.directory}}?fileExist=Ignore&chmod=666&chmodDirectory=666");
    }

    // checks that a file is not more than 2 days old
    @Bean
    GenericFileFilter notTooOld() {
        return file -> {
            String fileName = file.getFileNameOnly();
            long fileLastModified = file.getLastModified();

            // the difference in milliseconds for the time now
            // and the time that the file was last modified
            long diff = Calendar.getInstance().getTimeInMillis() - fileLastModified;
            // converts the difference to days
            long days = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);

            if (days <= 2) {
                log.info("File " + fileName + " is okay and will be consumed");
                return true;
            }
            //log.info("File " + fileName + " is too old, will not be consumed");
            return false;
        };
    }
}
