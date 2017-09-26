package dk.dma.enav.integrations;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileFilter;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spring.boot.FatJarRouter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class ForecastInRouter extends FatJarRouter {

    // standard template for the routes
    private static String routeTemplate = "ftp://%s@%s%s?password=%s&passiveMode=%s" +
            "&filter=#notTooOld&localWorkDirectory=/tmp&idempotent=true&idempotentRepository=#getIdempotentRepository&consumer.bridgeErrorHandler=true&binary=true&delay=15m";

    @Value("${dmi.passiveMode:false}")
    private String dmiPassiveMode;

    @Value("${fcoo.passiveMode:false}")
    private String fcooPassiveMode;

    @Value("${tracing:false}")
    private boolean tracing;

    private IdempotentRepository<String> idempotentRepository = new MemoryIdempotentRepository();

    @Override
    public void configure() {
        // options for tracing and debugging
        this.getContext().setTracing(tracing);
        this.onException(Exception.class)
                .maximumRedeliveries(6)
                .process(exchange -> log.error("Transfer failed for: " + exchange.getIn().getHeader(Exchange.FILE_NAME_ONLY)));

        String dmiFTP = String.format(routeTemplate, "{{dmi.user}}", "{{dmi.server}}", "{{dmi.directory}}", "{{dmi.password}}", dmiPassiveMode);

        // create the dmi route
        from(dmiFTP)
                .routeId("dmiRoute")
                .to("file://{{dmi.download.directory}}?fileExist=Ignore&chmod=666&chmodDirectory=666")
                .process(exchange -> log.info("Transfer succeeded for: " + exchange.getIn().getHeader(Exchange.FILE_NAME_ONLY)));

        String fcooFTP = String.format(routeTemplate, "{{fcoo.user}}", "{{fcoo.server}}", "{{fcoo.directory}}", "{{fcoo.password}}", fcooPassiveMode);

        // create the fcoo route
        from(fcooFTP)
                .routeId("fcooRoute")
                .to("file://{{fcoo.download.directory}}?fileExist=Ignore&chmod=666&chmodDirectory=666")
                .process(exchange -> log.info("Transfer succeeded for: " + exchange.getIn().getHeader(Exchange.FILE_NAME_ONLY)));
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

            if (days <= 2 && !this.idempotentRepository.contains(file.getAbsoluteFilePath())) {
                log.debug("File " + fileName + " is okay and will be consumed");
                return true;
            }
            //log.info("File " + fileName + " is too old, will not be consumed");
            return false;
        };
    }

    @Bean
    IdempotentRepository<String> getIdempotentRepository() {
        return this.idempotentRepository;
    }
}
