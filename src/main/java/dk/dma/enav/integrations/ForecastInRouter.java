package dk.dma.enav.integrations;

import org.apache.camel.component.file.GenericFileFilter;
import org.apache.camel.spring.boot.FatJarRouter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import javax.management.MalformedObjectNameException;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class ForecastInRouter extends FatJarRouter {

    // where the dmi consumer should consume from
    private String dmiRoute = String.format("ftp://%s@%s%s?password=%s&passiveMode=true" +
            "&filter=#notTooOld&idempotent=true&consumer.bridgeErrorHandler=true&binary=true&delay=15m",
            "{{dmi.user}}", "{{dmi.server}}", "{{dmi.directory}}", "{{dmi.password}}");

    // where the fcoo consumer should consume from
    private String fcooRoute = String.format("ftp://%s@%s%s?password=%s&passiveMode=true" +
            "&filter=#notTooOld&idempotent=true&consumer.bridgeErrorHandler=true&binary=true&delay=15m",
            "{{fcoo.user}}", "{{fcoo.server}}", "{{fcoo.directory}}", "{{fcoo.password}}");

    @Override
    public void configure() {
        this.getContext().setTracing(true);
        from(dmiRoute)
                .routeId("dmiRoute")
                .to("file://{{dmi.download.directory}}?fileExist=Ignore");

        from(fcooRoute)
                .routeId("fcooRoute")
                .to("file://{{fcoo.download.directory}}?fileExist=Ignore");
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

            if (days < 2) {
                //log.info("File " + fileName + " is okay");
                return true;
            }
            //log.info("File " + fileName + " is too old");
            return false;
        };
    }
}
