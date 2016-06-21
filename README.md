# forecast-in

Integration point responsible for fetching forecast data files from various providers.

## Prerequisites

* Java 8 or later

* A file called application.properties

## Configuration

The application.properties must be placed in src/main/java/dk.dma.enav.integrations/resources.
It must at least contain these lines:

    dmi.server = <ftp server address>
    dmi.user = <ftp user>
    dmi.password = <ftp password>
    dmi.directory = <ftp directory>
    dmi.download.directory = ${user.home}/arcticweb/dmi-prognoses
    
    fcoo.server = <ftp server address>
    fcoo.user = <ftp user>
    fcoo.password = <ftp password>
    fcoo.directory = <ftp directory>
    fcoo.download.directory = ${user.home}/arcticweb/fcoo-prognoses
    
To change the default placement of logging files the value of logging.file must be overridden.

## Execution

To run the program you can execute it the following way:

    chmod +x forecastIn.sh
    ./forecastIn.sh
    
Or directly with maven:

    mvn spring-boot:run
    
A Docker based setup is also found in the [docker](docker/) directory. 
    
