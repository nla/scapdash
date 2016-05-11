package scapdash;

import com.googlecode.flyway.core.Flyway;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.logging.PrintStreamLog;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.helpers.MapResultAsBean;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.vibur.dbcp.ViburDBCPDataSource;

import javax.sql.DataSource;
import java.util.Date;
import java.util.List;

import static scapdash.Util.getenv;

public interface Db extends Transactional<Db> {

    static Db open() {
        ViburDBCPDataSource ds = new ViburDBCPDataSource();
        ds.setName("ScapdashDB");
        ds.setJdbcUrl(getenv("DB_URL", "jdbc:h2:mem:scapdash"));
        ds.setUsername(getenv("DB_USERNAME", "scapdash"));
        ds.setPassword(getenv("DB_PASSWORD", "scapdash"));
        ds.start();
        return open(ds);
    }

    static Db open(DataSource ds) {
        Flyway flyway = new Flyway();
        flyway.setDataSource(ds);
        flyway.setLocations("scapdash/migration");
        flyway.migrate();

        DBI dbi = new DBI(ds);

        if (getenv("DEBUG_SQL", "0").equals("1")) {
            dbi.setSQLLog(new PrintStreamLog() {
                @Override
                public void logReleaseHandle(Handle h) {
                    // suppress
                }

                @Override
                public void logObtainHandle(long time, Handle h) {
                    // suppress
                }
            });
        }

        return dbi.onDemand(Db.class);
    }

    @SqlUpdate("INSERT INTO checkin (host_id, checkin_date) VALUES (:host_id, :checkin_date)")
    @GetGeneratedKeys
    int insertCheckin(@Bind("host_id") int hostId, @Bind("checkin_date") Date checkinDate);

    @SqlUpdate("INSERT INTO checkin_result (checkin_id, advisory_id, value) VALUES (:checkin_id, :advisory_id, :value)")
    @GetGeneratedKeys
    int insertCheckinResult(@Bind("checkin_id") int checkinId, @Bind("advisory_id") int advisory_id, @Bind("value") int value);

    @SqlQuery("SELECT id FROM advisory WHERE xml_ref = :xml_ref")
    Integer findAdvisoryIdByXmlRef(@Bind("xml_ref") String xmlRef);

    @SqlQuery("SELECT id FROM host WHERE name = :name")
    Integer findHostIdByName(@Bind("name") String name);

    @SqlUpdate("INSERT INTO host (name) VALUES (:name)")
    @GetGeneratedKeys
    int insertHost(@Bind("name") String name);

    @SqlUpdate("INSERT INTO advisory (xml_ref, title, severity) VALUES (:xml_ref, :title, :severity)")
    @GetGeneratedKeys
    int insertAdvisory(@Bind("xml_ref") String xmlRef, @Bind("title") String title, @Bind("severity") String severity);

    @SqlUpdate("INSERT INTO advisory_reference (advisory_id, system, identifier) VALUES (:advisory_id, :system, :identifier)")
    int insertAdvisoryReference(@Bind("advisory_id") int advisoryId, @Bind("system") String system, @Bind("identifier") String identifier);

    @SqlQuery("SELECT host.name hostname," +
            "COUNT(*) total," +
            "SUM(CASE WHEN advisory.severity = 'low' THEN 1 ELSE 0 END) lowCount, " +
            "SUM(CASE WHEN advisory.severity = 'medium' THEN 1 ELSE 0 END) mediumCount, " +
            "SUM(CASE WHEN advisory.severity = 'high' THEN 1 ELSE 0 END) highCount, " +
            "checkin.checkin_date checkinDate " +
            "FROM host " +
            "LEFT JOIN checkin ON host.id = checkin.host_id AND checkin.id = (SELECT MAX(id) FROM checkin WHERE checkin.host_id = host.id) " +
            "LEFT JOIN checkin_result ON checkin_result.checkin_id = checkin.id " +
            "LEFT JOIN advisory ON advisory.id = checkin_result.advisory_id " +
            "WHERE checkin_result.value = 0 " +
            "GROUP BY host.id")
    @MapResultAsBean
    List<HostSummaryRow> hostSummary();

    class HostSummaryRow {
        private String hostname;
        private int total;
        private int lowCount;
        private int mediumCount;
        private int highCount;
        private Date checkinDate;

        @Override
        public String toString() {
            return "HostSummaryRow{" +
                    "hostname='" + hostname + '\'' +
                    ", total='" + total + '\'' +
                    ", lowCount=" + lowCount +
                    ", mediumCount=" + mediumCount +
                    ", highCount=" + highCount +
                    ", checkinDate=" + checkinDate +
                    '}';
        }

        public String getHostname() {
            return hostname;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }

        public int getLowCount() {
            return lowCount;
        }

        public void setLowCount(int lowCount) {
            this.lowCount = lowCount;
        }

        public int getMediumCount() {
            return mediumCount;
        }

        public void setMediumCount(int mediumCount) {
            this.mediumCount = mediumCount;
        }

        public int getHighCount() {
            return highCount;
        }

        public void setHighCount(int highCount) {
            this.highCount = highCount;
        }

        public Date getCheckinDate() {
            return checkinDate;
        }

        public void setCheckinDate(Date checkinDate) {
            this.checkinDate = checkinDate;
        }
    }

    @SqlQuery("SELECT advisory.title title, advisory.severity severity " +
            "FROM checkin " +
            "LEFT JOIN checkin_result ON checkin_result.checkin_id = checkin.id " +
            "LEFT JOIN advisory ON advisory.id = checkin_result.advisory_id " +
            "WHERE checkin.id = (SELECT MAX(id) FROM checkin WHERE checkin.host_id = :host_id) " +
            "AND checkin_result.value = 0 ")
    @MapResultAsBean
    List<AdvisoryRow> findAdvisoriesByHostId(@Bind("host_id") int hostId);

    class AdvisoryRow {
        private String title;
        private String severity;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public String getIdentifier() {
            String[] parts = title.split(":", 3);
            return parts[0] + ":" + parts[1];
        }

        public String getBaseTitle() {
            return title.split(":", 3)[2];
        }

        public String getUrl() {
            String identifier = getIdentifier();
            if (identifier.startsWith("RHSA-")) {
                return "https://rhn.redhat.com/errata/" + identifier.replace(':', '-') + ".html";
            } else {
                return null;
            }
        }
    }



}
