package dk.dma.enav.integrations;


import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.file.FileEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.util.Date;
import java.util.GregorianCalendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ForecastInRouter.class)
@ActiveProfiles("unittest")
public class ForecastInTest {

    @Autowired
    private ModelCamelContext context;

    private FakeFtpServer fakeFtpServer;

    @Produce(uri="file:target/test-classes/idempotent")
    private ProducerTemplate producer;

    // Setup a basic environment before running the tests
    @Before
    public void setUp() {
        fakeFtpServer = new FakeFtpServer();
        fakeFtpServer.setServerControlPort(0);
        fakeFtpServer.addUserAccount(new UserAccount("user", "password", "/target/test-classes"));
        FileSystem fileSystem = new UnixFakeFileSystem();
        FileEntry dmi1 = new FileEntry("/target/test-classes/DMI1.nc");
        dmi1.setLastModified(new Date());
        fileSystem.add(dmi1);
        FileEntry dmi2 = new FileEntry("/target/test-classes/DMI2.nc");
        dmi2.setLastModified(new GregorianCalendar(2011, 9, 30).getTime());
        fileSystem.add(dmi2);
        fakeFtpServer.setFileSystem(fileSystem);
    }

    @Test
    public void testNotTooOldFilter() throws Exception {
        fakeFtpServer.start();
        int port = fakeFtpServer.getServerControlPort();
        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                replaceFromWith("ftp://user@127.0.0.1:" + port +"?password=password&filter=#notTooOld&passiveMode=true");
                weaveByType(ToDefinition.class).selectFirst().replace().to("mock:end");
            }
        });
        MockEndpoint end = context.getEndpoint("mock:end", MockEndpoint.class);
        end.expectedMessageCount(1);

        end.assertIsSatisfied();

        // Make sure that the received file has the correct file name
        String fileName = (String) end.getReceivedExchanges().get(0).getIn().getHeader(Exchange.FILE_NAME_ONLY);
        assertEquals(fileName, "DMI1.nc");

        fakeFtpServer.stop();
    }

    @Test
    public void testIdempotent() throws Exception {
        String uri = "file:target/test-classes/idempotent?idempotent=true&delay=10";
        context.getRouteDefinitions().get(1).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                replaceFromWith(uri);
                weaveByType(ToDefinition.class).selectFirst().replace().to("mock:endpoint");
            }
        });
        MockEndpoint end = context.getEndpoint("mock:endpoint", MockEndpoint.class);
        end.expectedMessageCount(1);

        producer.sendBodyAndHeader("file://target/test-classes/idempotent", Exchange.FILE_NAME, "FCOO1.nc");

        end.assertIsSatisfied();

        String fileName = (String) end.getReceivedExchanges().get(0).getIn().getHeader(Exchange.FILE_NAME_ONLY);
        assertEquals(fileName, "FCOO1.nc");

        // reset the mock
        end.reset();
        end.expectedMessageCount(0);

        // move file back
        File file = new File("target/test-classes/idempotent/.camel/FCOO1.nc");
        File renamed = new File("target/test-classes/idempotent/FCOO1.nc");
        file.renameTo(renamed);

        producer.sendBodyAndHeader("file://target/test-classes/idempotent", Exchange.FILE_NAME, "FCOO1.nc");

        // let some time pass to let the consumer try to consume even though it cannot
        Thread.sleep(100);
        end.assertIsSatisfied();

        FileEndpoint fe = context.getEndpoint(uri, FileEndpoint.class);
        assertNotNull(fe);

        // Make sure that there are no incoming messages
        MemoryIdempotentRepository repo = (MemoryIdempotentRepository) fe.getInProgressRepository();
        assertEquals("Should be no in-progress files", 0, repo.getCacheSize());
    }
}