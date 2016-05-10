package scapdash;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vibur.dbcp.ViburDBCPDataSource;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static scapdash.Util.getenv;

public class DbTest {

    private static Db db;
    private static ViburDBCPDataSource ds;

    @BeforeClass
    public static void openDb() throws IOException, XMLStreamException {
        ds = new ViburDBCPDataSource();
        ds.setName("ScapdashTestDB");
        ds.setJdbcUrl(getenv("DB_URL", "jdbc:h2:mem:scapdash-junit"));
        ds.setUsername(getenv("DB_USERNAME", "scapdashjunit"));
        ds.setPassword(getenv("DB_PASSWORD", "scapdashjunit"));
        ds.start();
        db = Db.open(ds);

        try (InputStream stream = ResultsParser.class.getResourceAsStream("test-results.xml")) {
            XMLStreamReader xml = XMLInputFactory.newInstance().createXMLStreamReader(stream);
            ResultsParser parser = new ResultsParser(new ResultsParser.DbInsertHandler(db));
            parser.parse(xml);
        }
    }

    @AfterClass
    public static void closeDb() {
        ds.close();
    }

    @Test
    public void hostSummary() throws Exception {
        List<Db.HostSummaryRow> results = db.hostSummary();
        assertEquals(1, results.size());
        Db.HostSummaryRow row = results.get(0);
        assertEquals("test.example.org", row.getHostname());
        assertEquals(1, row.getTotal());
        assertEquals(1, row.getLowCount());
        assertEquals(0, row.getMediumCount());
        assertEquals(0, row.getHighCount());
    }
}