package scapdash;

import org.omg.CORBA.portable.InputStream;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 * Parses a SCAP results.xml file
 */
public class ResultsParser {

    public ResultsParser(Handler handler) {
        this.handler = handler;
    }

    interface Handler {
        int checkin(String host);
        void checkinResult(int checkinId, String idref, CheckinResult value);
        int advisory(String xmlRef, String title, String severity);
        void advisoryReference(int advisoryId, String system, String identifier);
    }

    Handler handler;

    void parseBenchmark(XMLStreamReader xml) throws XMLStreamException {
        while (xml.nextTag() == START_ELEMENT) {
            switch (xml.getLocalName()) {
                case "TestResult":
                    parseTestResult(xml);
                    break;
                case "Rule":
                    parseRule(xml);
                    break;
                case "status":
                case "title":
                case "description":
                case "version":
                case "model":
                    skipChildTags(xml);
                    break;
                default:
                    throw new IllegalStateException("unexpected tag: " + xml.getLocalName());
            }
        }
    }

    void parse(XMLStreamReader xml) throws XMLStreamException {
        if (xml.nextTag() == START_ELEMENT && "Benchmark".equals(xml.getLocalName())) {
            parseBenchmark(xml);
        } else {
            throw new IllegalStateException("expected root 'Benchmark' tag, not " + xml.getLocalName());
        }

    }

    private void parseRule(XMLStreamReader xml) throws XMLStreamException {
        String xmlRef = xml.getAttributeValue(null, "id");
        String severity = xml.getAttributeValue(null, "severity");
        String title = null;
        String system = null;
        String identifier = null;
        Integer advisoryId = null;
        while (xml.nextTag() == START_ELEMENT) {
            switch (xml.getLocalName()) {
                case "title":
                    title = xml.getElementText();
                    advisoryId = handler.advisory(xmlRef, title, severity);
                    break;
                case "ident":
                    system = xml.getAttributeValue(null, "system");
                    identifier = xml.getElementText();
                    if (advisoryId == null) {
                        throw new IllegalStateException("expected title tag already");
                    }
                    handler.advisoryReference(advisoryId, system, identifier);
                    break;
                case "check":
                    skipChildTags(xml);
                    break;
                default:
                    throw new IllegalStateException("unexpected tag: " + xml.getLocalName());
            }
        }
    }

    private void parseTestResult(XMLStreamReader xml) throws XMLStreamException {
        Integer checkinId = null;
        while (xml.nextTag() == START_ELEMENT) {
            switch (xml.getLocalName()) {
                case "target":
                    String host = xml.getElementText();
                    checkinId = handler.checkin(host);
                    break;
                case "rule-result":
                    if (checkinId == null) {
                        throw new IllegalStateException("expected to have seen 'target' tag by now");
                    }
                    parseRuleResult(xml, checkinId);
                    break;
                case "identity":
                case "benchmark":
                case "title":
                case "target-address":
                case "target-facts":
                case "score":
                    skipChildTags(xml);
                    break;
                default:
                    throw new IllegalStateException("unexpected tag: " + xml.getLocalName());
            }
        }
    }

    private void parseRuleResult(XMLStreamReader xml, int checkinId) throws XMLStreamException {
        String idref = xml.getAttributeValue(null, "idref");
        CheckinResult value = CheckinResult.ERROR;
        while (xml.nextTag() == START_ELEMENT) {
            switch (xml.getLocalName()) {
                case "result":
                    switch (xml.getElementText()) {
                        case "pass":
                            value = CheckinResult.PASS;
                            break;
                        case "fail":
                            value = CheckinResult.FAIL;
                            break;
                        default:
                            value = CheckinResult.ERROR;
                            break;
                    }
                    break;
                case "ident":
                case "check":
                    skipChildTags(xml);
                    break;
                default:
                    throw new IllegalStateException("unexpected tag: " + xml.getLocalName());
            }
        }
        handler.checkinResult(checkinId, idref, value);
    }

    private void skipChildTags(XMLStreamReader xml) throws XMLStreamException {
        for (int depth = 1; depth > 0;) {
            if (xml.next() == START_ELEMENT) {
                depth++;
            } else if (xml.getEventType() == END_ELEMENT) {
                depth--;
            }
        }
    }

    static class DbInsertHandler implements Handler {

        Db db;

        public DbInsertHandler(Db db) {
            this.db = db;
        }

        @Override
        public int checkin(String host) {
            Integer hostId = db.findHostIdByName(host);

            if (hostId == null) {
                hostId = db.insertHost(host);
            }

            return db.insertCheckin(hostId, new Date());
        }

        @Override
        public void checkinResult(int checkinId, String idref, CheckinResult value) {
            Integer advisoryId = db.findAdvisoryIdByXmlRef(idref);

            if (advisoryId == null) {
                throw new RuntimeException("unknown advisory: " + idref);
            }

            db.insertCheckinResult(checkinId, advisoryId, value.ordinal());
        }

        @Override
        public int advisory(String xmlRef, String title, String severity) {
            Integer advisoryId = db.findAdvisoryIdByXmlRef(xmlRef);

            if (advisoryId == null) {
                advisoryId = db.insertAdvisory(xmlRef, title, severity);
            }

            return advisoryId;
        }

        @Override
        public void advisoryReference(int advisoryId, String system, String identifier) {
            //FIXME: db.insertAdvisoryReference(advisoryId, system, identifier);
        }
    }

    public static void main(String args[]) throws XMLStreamException, IOException {
        if (args.length == 0) {
            System.err.println("Usage: ResultsParser results.xml");
            System.err.println("Loads results.xml into the Scapdash database.");
            System.exit(1);
        }

        Db db = Db.open();

        try (FileInputStream stream = new FileInputStream(args[0])) {
            XMLStreamReader xml = XMLInputFactory.newInstance().createXMLStreamReader(stream);

            long start = System.currentTimeMillis();

            db.inTransaction((db1, transactionStatus) -> {
                ResultsParser parser = new ResultsParser(new DbInsertHandler(db1));
                parser.parse(xml);
                return null;
            });

            System.out.println("Completed in " + (System.currentTimeMillis() - start) + " ms");
        }
    }
}
