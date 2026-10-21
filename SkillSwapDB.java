import java.sql.*;
import java.util.*;

/**
 * Simple database for SkillSwap (SQLite + JDBC).
 *
 * Needs the SQLite JDBC driver jar (sqlite-jdbc-x.x.x.jar) on the classpath.
 * Compile: javac SkillSwapDB.java
 * Run:     java -cp .:sqlite-jdbc.jar SkillSwapDB      (use ; instead of : on Windows)
 */
public class SkillSwapDB {

    private static final String URL = "jdbc:sqlite:skillswap.db";
    private Connection con;

    public SkillSwapDB() throws SQLException {
        con = DriverManager.getConnection(URL);
        createTables();
    }

    // ---------- Tables ----------
    private void createTables() throws SQLException {
        try (Statement st = con.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS students ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "name TEXT NOT NULL,"
                    + "major TEXT)");
            // type is 'teach' or 'learn'
            st.execute("CREATE TABLE IF NOT EXISTS skills ("
                    + "student_id INTEGER NOT NULL,"
                    + "skill TEXT NOT NULL,"
                    + "type TEXT NOT NULL,"
                    + "PRIMARY KEY (student_id, skill, type))");
            // status is 'pending', 'accepted', 'rejected' or 'done'
            st.execute("CREATE TABLE IF NOT EXISTS requests ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "from_id INTEGER NOT NULL,"
                    + "to_id INTEGER NOT NULL,"
                    + "status TEXT NOT NULL DEFAULT 'pending')");
        }
    }

    // ---------- Students ----------
    public int addStudent(String name, String major, String[] teach, String[] learn) throws SQLException {
        String sql = "INSERT INTO students (name, major) VALUES (?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, major);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            keys.next();
            int id = keys.getInt(1);
            for (String s : teach) addSkill(id, s, "teach");
            for (String s : learn) addSkill(id, s, "learn");
            return id;
        }
    }

    public void addSkill(int studentId, String skill, String type) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT OR IGNORE INTO skills VALUES (?, ?, ?)")) {
            ps.setInt(1, studentId);
            ps.setString(2, skill);
            ps.setString(3, type);
            ps.executeUpdate();
        }
    }

    public void removeSkill(int studentId, String skill, String type) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM skills WHERE student_id=? AND skill=? AND type=?")) {
            ps.setInt(1, studentId);
            ps.setString(2, skill);
            ps.setString(3, type);
            ps.executeUpdate();
        }
    }

    public void updateProfile(int id, String name, String major) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE students SET name=?, major=? WHERE id=?")) {
            ps.setString(1, name);
            ps.setString(2, major);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    public void printAllStudents() throws SQLException {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM students")) {
            while (rs.next()) {
                int id = rs.getInt("id");
                System.out.println(id + ". " + rs.getString("name") + " (" + rs.getString("major") + ")");
                System.out.println("   Teaches: " + getSkills(id, "teach"));
                System.out.println("   Learns:  " + getSkills(id, "learn"));
            }
        }
    }

    public List<String> getSkills(int studentId, String type) throws SQLException {
        List<String> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT skill FROM skills WHERE student_id=? AND type=?")) {
            ps.setInt(1, studentId);
            ps.setString(2, type);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rs.getString(1));
        }
        return list;
    }

    // ---------- Search & matching ----------
    /** Students whose name or skills contain the text. */
    public void search(String text) throws SQLException {
        String sql = "SELECT DISTINCT s.id, s.name FROM students s "
                + "LEFT JOIN skills k ON k.student_id = s.id "
                + "WHERE s.name LIKE ? OR k.skill LIKE ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, "%" + text + "%");
            ps.setString(2, "%" + text + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) System.out.println(rs.getInt(1) + ". " + rs.getString(2));
        }
    }

    /** Mutual matches: they teach what I learn AND they learn what I teach. */
    public void printMutualMatches(int myId) throws SQLException {
        String sql = "SELECT DISTINCT s.id, s.name FROM students s "
                + "WHERE s.id <> ? "
                + "AND EXISTS (SELECT 1 FROM skills a, skills b WHERE a.student_id = s.id AND a.type='teach' "
                + "            AND b.student_id = ? AND b.type='learn' AND a.skill = b.skill COLLATE NOCASE) "
                + "AND EXISTS (SELECT 1 FROM skills a, skills b WHERE a.student_id = s.id AND a.type='learn' "
                + "            AND b.student_id = ? AND b.type='teach' AND a.skill = b.skill COLLATE NOCASE)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, myId);
            ps.setInt(2, myId);
            ps.setInt(3, myId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) System.out.println("Match: " + rs.getString("name"));
        }
    }

    // ---------- Requests ----------
    public int sendRequest(int fromId, int toId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO requests (from_id, to_id) VALUES (?, ?)")) {
            ps.setInt(1, fromId);
            ps.setInt(2, toId);
            return ps.executeUpdate();
        }
    }

    public void setRequestStatus(int requestId, String status) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE requests SET status=? WHERE id=?")) {
            ps.setString(1, status);
            ps.setInt(2, requestId);
            ps.executeUpdate();
        }
    }

    public void cancelRequest(int requestId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM requests WHERE id=?")) {
            ps.setInt(1, requestId);
            ps.executeUpdate();
        }
    }

    public void printRequestsFor(int studentId) throws SQLException {
        String sql = "SELECT r.id, s.name, r.status FROM requests r "
                + "JOIN students s ON s.id = r.from_id WHERE r.to_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                System.out.println("Request #" + rs.getInt(1) + " from " + rs.getString(2) + " [" + rs.getString(3) + "]");
        }
    }

    // ---------- Sample data (same students as the website) ----------
    private void seed() throws SQLException {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM students")) {
            if (rs.next() && rs.getInt(1) > 0) return; // already has data
        }
        addStudent("Priya Nair", "Computer Science, Year 3", new String[]{"Python", "Data Analysis"}, new String[]{"Guitar", "Public Speaking"});
        addStudent("Daniel Okafor", "Music, Year 2", new String[]{"Guitar", "Music Theory"}, new String[]{"Python", "Video Editing"});
        addStudent("Mei Lin", "Design, Year 4", new String[]{"Figma", "Video Editing"}, new String[]{"Spanish", "Excel"});
        addStudent("Lucas Fernandez", "Business, Year 3", new String[]{"Spanish", "Public Speaking"}, new String[]{"Figma", "Python"});
        addStudent("Aisha Khan", "Economics, Year 2", new String[]{"Excel", "Statistics"}, new String[]{"Photography", "Guitar"});
        addStudent("Tom Becker", "Film Studies, Year 1", new String[]{"Photography", "Video Editing"}, new String[]{"Statistics", "Excel"});
        addStudent("Sara Johansson", "Biology, Year 3", new String[]{"Statistics", "Academic Writing"}, new String[]{"Python", "Figma"});
        addStudent("Ravi Menon", "Engineering, Year 2", new String[]{"CAD Modelling", "Excel"}, new String[]{"Public Speaking", "Academic Writing"});
    }

    public void close() throws SQLException { con.close(); }

    // ---------- Demo ----------
    public static void main(String[] args) throws SQLException {
        SkillSwapDB db = new SkillSwapDB();
        db.seed();

        // add "me" (the logged-in student on the Profile page)
        int me = db.addStudent("Alex Morgan", "Psychology, Year 2",
                new String[]{"Excel"}, new String[]{"Python", "Guitar"});

        System.out.println("--- All students ---");
        db.printAllStudents();

        System.out.println("\n--- Search 'python' ---");
        db.search("python");

        System.out.println("\n--- My mutual matches ---");
        db.printMutualMatches(me);

        System.out.println("\n--- Send request to Priya (id 1), she accepts ---");
        db.sendRequest(me, 1);
        db.printRequestsFor(1);
        db.setRequestStatus(1, "accepted");
        db.printRequestsFor(1);

        db.close();
    }
}
