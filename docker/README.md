# Dockerized forecast-in

As described in [configuration](https://github.com/maritime-web/forecast-in/blob/master/README.md#configuration) you need a file called application.properties. 
This needs to be placed in `$HOME/arcticweb/properties`. 
In this file you need to include the string `logging.file` with no assignment. 

To start the container do

    docker run --name forecastin -v $HOME/arcticweb:/data/arcticweb dmadk/forecast-in
    
