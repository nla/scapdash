package scapdash;

import org.junit.Test;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import java.io.InputStream;

import static org.junit.Assert.*;

public class ResultsParserTest {
    @Test
    public void parse() throws Exception {
        try (InputStream stream = ResultsParser.class.getResourceAsStream("test-results.xml")) {
            XMLStreamReader xml = XMLInputFactory.newInstance().createXMLStreamReader(stream);
            ResultsParser parser = new ResultsParser(new ResultsParser.Handler() {

                @Override
                public int checkin(String host) {
                    assertEquals("test.example.org", host);
                    return 0;
                }

                @Override
                public void checkinResult(int checkinId, String idref, CheckinResult value) {
                    assertNotNull(idref);
                    assertNotNull(value);
                }

                @Override
                public int advisory(String xmlRef, String title, String severity) {
                    assertNotNull(xmlRef);
                    assertNotNull(title);
                    assertNotNull(severity);
                    return 0;
                }

                @Override
                public void advisoryReference(int advisoryId, String system, String identifier) {
                    assertNotNull(system);
                    assertNotNull(identifier);

                }
            });
            parser.parse(xml);
        }
    }

}