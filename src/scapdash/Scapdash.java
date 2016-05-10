package scapdash;

import spark.Spark;

import javax.servlet.ServletInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import static scapdash.Util.getenv;
import static spark.Spark.*;

public class Scapdash {
    public static void main(String args[]) {
        Db db = Db.open();

        ipAddress(getenv("HOST", "localhost"));
        port(Integer.parseInt(getenv("PORT", "4567")));

        get("/", (req, res) -> {
            // FIXME: template
            String out = "<pre>HOST TOTAL HIGH MEDIUM LOW LAST_CHECKIN\n";
            
            for (Db.HostSummaryRow row : db.hostSummary()) {
                out += row.getHostname() + " " + row.getTotal() + " " + row.getHighCount() + " " + row.getMediumCount() + " " + row.getLowCount() + " " + row.getCheckinDate() + "\n";
            }

            return out + "</pre>";
        });

        post("/checkin", (req, res) -> {
            try (ServletInputStream stream = req.raw().getInputStream()) {
                XMLStreamReader xml = XMLInputFactory.newInstance().createXMLStreamReader(stream);

                long start = System.currentTimeMillis();

                db.inTransaction((db1, transactionStatus) -> {
                    ResultsParser parser = new ResultsParser(new ResultsParser.DbInsertHandler(db1));
                    parser.parse(xml);
                    return null;
                });

                return "OK. Completed data load in " + (System.currentTimeMillis() - start) + " ms\n";
            }
        });

        exception(Exception.class, (e, request, response) -> {
            response.status(500);
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            response.body(sw.toString());
        });
    }
}
