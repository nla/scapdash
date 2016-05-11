package scapdash;

import spark.ModelAndView;
import spark.Spark;
import spark.template.jade.JadeTemplateEngine;

import javax.servlet.ServletInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;

import static scapdash.Util.getenv;
import static spark.Spark.*;

public class Scapdash {
    public static void main(String args[]) {
        Db db = Db.open();
        JadeTemplateEngine jade = new JadeTemplateEngine("scapdash/templates");

        ipAddress(getenv("HOST", "localhost"));
        port(Integer.parseInt(getenv("PORT", "4567")));

        get("/", (req, res) -> render("summary", "hostSummary", db.hostSummary()), jade);

        get("/hosts/:hostname", (req, res) -> {
            String hostname = req.params(":hostname");
            Integer hostId = db.findHostIdByName(hostname);
            if (hostId == null) {
                halt(404);
            }

            return render("host", "hostname", hostname, "advisories", db.findAdvisoriesByHostId(hostId));
        }, jade);

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
            response.type("text/plain");
            response.body(sw.toString());
        });
    }

    static ModelAndView render(String view, Object... keysAndValues) {
        assert(keysAndValues.length % 2 == 0);
        HashMap<String, Object> model = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            model.put((String)keysAndValues[i], keysAndValues[i + 1]);
        }
        return new ModelAndView(model, view);
    }
}
