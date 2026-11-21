import java.awt.BorderLayout; // layout for placing parts of a window
import java.awt.Component; // a parent window/component for dialogs
import java.awt.Dimension; // width and height of a component
import java.awt.FlowLayout; // layout that places buttons in a row
import java.awt.Frame; // a window (used as the owner of dialogs)
import java.awt.GridLayout; // layout that places components in a grid
import java.awt.event.ActionEvent; // describes a button click
import java.awt.event.ActionListener; // lets a class react to button clicks
import java.io.File; // used to show where the CSV file was saved
import java.io.FileNotFoundException; // thrown when a file cannot be created
import java.io.PrintWriter; // writes text into a file
import java.sql.Connection; // connection to the database
import java.sql.DriverManager; // opens the connection to the database
import java.sql.PreparedStatement; // a safe SQL command with ? placeholders
import java.sql.ResultSet; // the rows returned by a SELECT command
import java.sql.SQLException; // thrown when a database command fails
import java.sql.Statement; // a plain SQL command
import java.text.ParseException; // thrown when a date text cannot be read
import java.text.SimpleDateFormat; // formats a date into readable text
import java.util.ArrayList; // resizable list
import java.util.Date; // current date and time
import java.util.Random; // random numbers for picking a student
import javax.swing.BorderFactory; // adds empty space around components
import javax.swing.JButton; // clickable button
import javax.swing.JDialog; // pop-up window
import javax.swing.JLabel; // text label
import javax.swing.JOptionPane; // ready-made message and input dialogs
import javax.swing.JPanel; // container that groups components
import javax.swing.JScrollPane; // adds scroll bars to a component
import javax.swing.JTable; // table (rows and columns) like an inventory list
import javax.swing.JTextField; // one-line text box
import javax.swing.ListSelectionModel; // controls how rows are selected in a table
import javax.swing.table.DefaultTableModel; // the data inside a JTable

public class ClassCheck {

    /* program entry point. opens the database, then shows the start menu
     * (create a class record, open an existing one, or exit). */
    public static void main(String[] args) {

        // open (or create) the database
        Database db = null;
        try {
            db = new Database("classcheck");
        } catch (ClassNotFoundException e) {
            InputHelper.show("The MySQL driver was not found.\n\n"
                    + "Add the mysql-connector-j .jar file to the classpath, then run again.");
            System.exit(0);
        } catch (SQLException e) {
            InputHelper.show("Could not connect to the database:\n" + e.getMessage()
                    + "\n\nMake sure Laragon is open and MySQL is started (Start All).");
            System.exit(0);
        }

        // start menu
        String[] startOptions = {"Create New Class Record", "Open Existing Class Record",
            "Delete Class Record", "Exit"};
        boolean running = true;
        while (running) {
            int choice = InputHelper.choose("CLASSCHECK",
                    "Welcome to ClassCheck!\n\nWhat would you like to do?", startOptions);

            if (choice == 0) {
                createClassRecord(db);
            } else if (choice == 1) {
                running = openClassRecord(db); // false when the user chose Exit Program
            } else if (choice == 2) {
                deleteClassRecord(db);
            } else {
                running = false; // Exit button or window closed
            }
        }

        db.close();
        InputHelper.show("Thank you for using ClassCheck!");
    }

    /* asks for the Program and Block, creates the class record,
     * then opens the table editor so the students can be added. */
    private static void createClassRecord(Database db) {
        String name = null;
        boolean done = false;
        while (!done) {
            name = InputHelper.readText("CREATE CLASS RECORD\n\nEnter Program and Block:\n(Example: BSIT 1A)");
            if (name == null) {
                done = true; // Cancel
            } else if (name.length() == 0) {
                InputHelper.show("Program and Block cannot be empty.");
            } else if (db.findClass(name.toUpperCase()) != null) {
                InputHelper.show("A class record named \"" + name.toUpperCase() + "\" already exists.");
            } else {
                done = true;
            }
        }

        if (name != null) {
            ClassRecord cr = db.createClass(name.toUpperCase());
            if (cr != null) {
                InputHelper.show("Class record \"" + cr.name + "\" created.\n"
                        + "Next, add the students to the class list.");
                new StudentEditor(db, cr).open();
            }
        }
    }

    /* lets the user pick a class record from the list and delete it (after asking YES / NO).
     * the students, attendance, points and quizzes of that class are deleted too. */
    private static void deleteClassRecord(Database db) {
        ArrayList<ClassRecord> classes = db.getClasses();

        if (classes.size() == 0) {
            InputHelper.show("There are no class records to delete.");
        } else {
            String[] labels = new String[classes.size()];
            for (int i = 0; i < classes.size(); i++) {
                labels[i] = classes.get(i).name;
            }
            int index = InputHelper.pickIndex("DELETE CLASS RECORD", "Select the class record to delete:", labels);
            if (index != -1) {
                ClassRecord cr = classes.get(index);
                boolean sure = InputHelper.askYesNo("Delete the class record \"" + cr.name + "\"?\n\n"
                        + "This also deletes its " + db.countStudents(cr.id) + " student(s) and all their\n"
                        + "attendance, points and quiz records.\n\nThis cannot be undone.");
                if (sure) {
                    if (db.deleteClass(cr.id)) {
                        InputHelper.show("Class record \"" + cr.name + "\" was deleted.");
                    }
                }
            }
        }
    }

    /* lets the user pick an existing class record and opens its menu.
     * returns false if the user chose Exit Program, true otherwise. */
    private static boolean openClassRecord(Database db) {
        boolean keepRunning = true;
        ArrayList<ClassRecord> classes = db.getClasses();

        if (classes.size() == 0) {
            InputHelper.show("There are no class records yet.\nCreate a new class record first.");
        } else {
            String[] labels = new String[classes.size()];
            for (int i = 0; i < classes.size(); i++) {
                labels[i] = classes.get(i).name;
            }
            int index = InputHelper.pickIndex("OPEN CLASS RECORD", "Select a class record:", labels);
            if (index != -1) {
                keepRunning = classMenu(db, classes.get(index));
            }
        }
        return keepRunning;
    }

    /* the menu of one class record. returns false if the user chose Exit Program,
     * true if the user chose Back to Class Records. */
    private static boolean classMenu(Database db, ClassRecord cr) {

        // objects shared by all features of this class record
        AttendanceManager attendance = new AttendanceManager(db);
        Recitation recitation = new Recitation(db, cr);
        PointsManager pointsManager = new PointsManager(db);
        QuizManager quizManager = new QuizManager(db);
        FinalRecord finalRecord = new FinalRecord(db);
        RecordViewer viewer = new RecordViewer(db, finalRecord);

        String[] options = {"Mark Attendance (Present / Absent)",
            "Mark Absent or Late (Second Check)",
            "Recitation Randomizer",
            "Manage Points",
            "Record Quiz Scores",
            "Edit Class List",
            "View Record (Daily / Overall)",
            "Generate Final Class Record",
            "Back to Class Records",
            "Exit Program"};

        int choice = 0;
        do {
            choice = InputHelper.choose("CLASSCHECK - " + cr.name,
                    "Class: " + cr.name + "\nToday: " + InputHelper.prettyDate(InputHelper.today())
                    + "\n\nChoose an option:", options);

            if (choice == -1) {
                choice = 8; // window closed: same as Back
            }

            // options 0 to 4 (attendance, second check, recitation, points, quiz)
            // and 7 (final record) need students in the class list
            boolean needsStudents = choice <= 4 || choice == 7;
            if (needsStudents && db.countStudents(cr.id) == 0) {
                InputHelper.show("There are no students in this class yet.\n"
                        + "Choose \"Edit Class List\" to add students.");
            } else {
                switch (choice) {
                    case 0:
                        attendance.mark(cr);
                        recitation.reset(); // attendance changed, rebuild the pool
                        break;
                    case 1:
                        attendance.markAbsentOrLate(cr);
                        recitation.reset(); // attendance changed, rebuild the pool
                        break;
                    case 2:
                        recitation.start();
                        break;
                    case 3:
                        pointsManager.run(cr);
                        break;
                    case 4:
                        quizManager.record(cr);
                        break;
                    case 5:
                        new StudentEditor(db, cr).open();
                        recitation.reset(); // the class list changed
                        break;
                    case 6:
                        viewer.run(cr);
                        break;
                    case 7:
                        finalRecord.generate(cr);
                        break;
                    default:
                        break; // 8 = back, 9 = exit
                }
            }
        } while (choice != 8 && choice != 9);

        return choice != 9; // false only when the user chose Exit Program
    }
}

/*helper methods for input, messages, buttons and tables.
 * all methods are static, so they are called as InputHelper.method(). */
class InputHelper {

    // Constant (uppercase). Returned by readInt when the user presses Cancel.
    // It has 10 digits, so isInteger() never accepts it as typed input.
    public static final int CANCELLED = -2147483648;

    /*shows an input box and returns the text typed (trimmed)
     * returns null if the user pressed Cancel.*/
    public static String readText(String message) {
        String input = JOptionPane.showInputDialog(message);
        if (input != null) {
            input = input.trim();
        }
        return input;
    }

    /*checks whether a String is a whole number (an optional leading minus is allowed)
     * only up to 9 digits are accepted so the value always fits in an int */
    public static boolean isInteger(String text) {
        boolean valid = true;
        int start = 0;

        if (text == null || text.length() == 0) {
            valid = false;
        } else {
            if (text.charAt(0) == '-') {
                start = 1;
                if (text.length() == 1) {
                    valid = false;
                }
            }
            if (text.length() - start > 9) {
                valid = false;
            }
            for (int i = start; i < text.length(); i++) {
                if (!Character.isDigit(text.charAt(i))) {
                    valid = false;
                }
            }
        }
        return valid;
    }

    /* keeps asking until a valid whole number is entered.
     * returns CANCELLED if the user pressed Cancel.*/
    public static int readInt(String message) {
        int result = 0;
        boolean done = false;
        while (!done) {
            String input = readText(message);
            if (input == null) {
                result = CANCELLED;
                done = true;
            } else if (isInteger(input)) {
                result = Integer.parseInt(input);
                done = true;
            } else {
                show("Please enter a whole number.");
            }
        }
        return result;
    }

    // asks a yes/no question using YES and NO buttons. Closing the window counts as NO.
    public static boolean askYesNo(String message) {
        return askYesNo(null, message);
    }

    // same as above, but the dialog belongs to a parent window
    public static boolean askYesNo(Component parent, String message) {
        String[] options = {"Yes", "No"};
        int pick = JOptionPane.showOptionDialog(parent, message, "ClassCheck",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        return pick == 0;
    }

    // shows a message box.
    public static void show(String message) {
        show(null, message);
    }

    // shows a message box that belongs to a parent window
    public static void show(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message);
    }

    /* shows a window with one button per option (stacked vertically).
     * returns the index of the clicked button, or -1 if the window was closed.*/
    public static int choose(String title, String message, String[] options) {
        ChoiceDialog dialog = new ChoiceDialog(title, message, options);
        return dialog.showDialog();
    }

    /* shows a drop-down list and returns the index of the chosen item.
     * returns -1 if the user pressed Cancel.*/
    public static int pickIndex(String title, String message, String[] labels) {
        Object picked = JOptionPane.showInputDialog(null, message, title,
                JOptionPane.QUESTION_MESSAGE, null, labels, labels[0]);
        int index = -1;
        for (int i = 0; i < labels.length; i++) {
            if (labels[i].equals(picked)) {
                index = i;
            }
        }
        return index;
    }

    // shows a read-only table (with a heading above it) in a message window
    public static void showTable(String heading, String[] headers, String[][] rows) {
        DefaultTableModel model = new DefaultTableModel(rows, headers) {
            public boolean isCellEditable(int row, int column) {
                return false; // the user cannot type inside the table
            }
        };
        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        if (headers.length > 1) {
            table.getColumnModel().getColumn(1).setPreferredWidth(220);
        }

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(780, 340));

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel(toHtml(heading)), BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        JOptionPane.showMessageDialog(null, panel, "ClassCheck", JOptionPane.PLAIN_MESSAGE);
    }

    // turns normal text (with \n line breaks) into HTML so a JLabel can show many lines
    public static String toHtml(String text) {
        String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html>" + safe.replace("\n", "<br>") + "</html>";
    }

    // makes a name look proper: "dela cruz" becomes "Dela Cruz"
    public static String properCase(String text) {
        String result = "";
        String t = text.trim();
        boolean capitalizeNext = true;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (capitalizeNext) {
                result += Character.toUpperCase(c);
            } else {
                result += Character.toLowerCase(c);
            }
            capitalizeNext = (c == ' ' || c == '-' || c == '\'' || c == '.');
        }
        return result;
    }

    // today's date as text, e.g. 2026-11-04 (this is the format stored in the database)
    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    // turns 2026-11-04 into "Wednesday, November 04, 2026"
    public static String prettyDate(String isoDate) {
        String result = isoDate;
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd").parse(isoDate);
            result = new SimpleDateFormat("EEEE, MMMM dd, yyyy").format(d);
        } catch (ParseException e) {
            result = isoDate; // keep the plain text if it cannot be read
        }
        return result;
    }

    // returns the full names of a list of students as an array (for drop-down lists)
    public static String[] namesOf(ArrayList<Student> students) {
        String[] names = new String[students.size()];
        for (int i = 0; i < students.size(); i++) {
            names[i] = students.get(i).getFullName();
        }
        return names;
    }
}

/* a small window with one big button per option (the easier replacement for typing a number).
 * ChoiceDialog is a JDialog and also an ActionListener, so it reacts to its own buttons. */
class ChoiceDialog extends JDialog implements ActionListener {

    private int selected = -1; // index of the clicked button, -1 if none

    public ChoiceDialog(String title, String message, String[] options) {
        super((Frame) null, title, true); // true = modal (blocks until it is closed)

        JLabel label = new JLabel(InputHelper.toHtml(message));
        label.setBorder(BorderFactory.createEmptyBorder(15, 20, 5, 20));

        JPanel buttons = new JPanel(new GridLayout(options.length, 1, 6, 6));
        buttons.setBorder(BorderFactory.createEmptyBorder(10, 20, 15, 20));
        for (int i = 0; i < options.length; i++) {
            JButton button = new JButton(options[i]);
            button.setActionCommand("" + i); // the button remembers its own index
            button.addActionListener(this);
            buttons.add(button);
        }

        setLayout(new BorderLayout());
        add(label, BorderLayout.NORTH);
        add(buttons, BorderLayout.CENTER);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        if (getWidth() < 360) {
            setSize(360, getHeight());
        }
        setLocationRelativeTo(null); // center on the screen
    }

    // runs when any button is clicked
    public void actionPerformed(ActionEvent e) {
        selected = Integer.parseInt(e.getActionCommand());
        dispose();
    }

    // shows the window, waits until it closes, then returns the clicked index
    public int showDialog() {
        setVisible(true);
        return selected;
    }
}

// parent class (superclass), holds the information common to every person
class Person {

    public String lastName;
    public String firstName;
    public String middleName; // empty text if the person has no middle name

    // Constructor: sets the three parts of the name
    public Person(String lastName, String firstName, String middleName) {
        this.lastName = lastName;
        this.firstName = firstName;
        this.middleName = middleName;
    }

    // returns the name as "Last, First Middle" (middle is left out if empty)
    public String getFullName() {
        String name = lastName + ", " + firstName;
        if (middleName.length() > 0) {
            name += " " + middleName;
        }
        return name;
    }

    // returns basic information. Child classes may override (replace) this method
    public String getInfo() {
        return getFullName();
    }
}

/* Student is a child class of Person (INHERITANCE)
 * it inherits the name and adds the database id and the total points */
class Student extends Person {

    public int id;     // primary key in the database
    public int points; // total points of the student

    public Student(int id, String lastName, String firstName, String middleName, int points) {
        super(lastName, firstName, middleName); // calls the Person constructor
        this.id = id;
        this.points = points;
    }

    // overrides Person.getInfo() (POLYMORPHISM): same method name, different result
    public String getInfo() {
        return getFullName() + " - Points: " + points;
    }

    public String toString() {
        return getFullName();
    }
}

// one class record (Program and Block) stored in the database
class ClassRecord {

    public int id;
    public String name;

    public ClassRecord(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public String toString() {
        return name;
    }
}

// one quiz stored in the database
class Quiz {

    public int id;
    public String date;
    public String title;
    public int overall; // the highest possible score

    public Quiz(int id, String date, String title, int overall) {
        this.id = id;
        this.date = date;
        this.title = title;
        this.overall = overall;
    }
}

/* all the database work is done here (Laragon MySQL).
 * other classes never write SQL; they only call these methods. */
class Database {

    // connection settings (Laragon defaults). Change them here if yours are different.
    private static final String DB_HOST = "localhost";
    private static final int DB_PORT = 3306;
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";

    private Connection conn;

    // connects to MySQL, creates the database if needed, then creates the tables if needed
    public Database(String dbName) throws SQLException, ClassNotFoundException {

        // load the driver: the MySQL connector first, the MariaDB connector if it is not found
        String prefix = "jdbc:mysql://";
        String options = "?useSSL=false&allowPublicKeyRetrieval=true";
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            Class.forName("org.mariadb.jdbc.Driver"); // throws if this one is missing too
            prefix = "jdbc:mariadb://";
            options = "";
        }

        conn = DriverManager.getConnection(prefix + DB_HOST + ":" + DB_PORT + "/" + options,
                DB_USER, DB_PASSWORD);

        Statement st = conn.createStatement();
        st.execute("CREATE DATABASE IF NOT EXISTS " + dbName + " CHARACTER SET utf8mb4");
        st.execute("USE " + dbName);

        // MySQL compares text without caring about upper/lower case, so "cruz" = "Cruz"
        // ON DELETE CASCADE: deleting a student also deletes their records
        st.execute("CREATE TABLE IF NOT EXISTS classes ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "name VARCHAR(50) NOT NULL UNIQUE) ENGINE=InnoDB");

        st.execute("CREATE TABLE IF NOT EXISTS students ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "class_id INT NOT NULL, "
                + "last_name VARCHAR(60) NOT NULL, "
                + "first_name VARCHAR(60) NOT NULL, "
                + "middle_name VARCHAR(60) NOT NULL DEFAULT '', "
                + "FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE) ENGINE=InnoDB");

        st.execute("CREATE TABLE IF NOT EXISTS attendance ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "student_id INT NOT NULL, "
                + "date VARCHAR(10) NOT NULL, "
                + "status VARCHAR(10) NOT NULL, "
                + "UNIQUE KEY one_mark_per_day (student_id, date), "
                + "FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE) ENGINE=InnoDB");

        st.execute("CREATE TABLE IF NOT EXISTS points_log ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "student_id INT NOT NULL, "
                + "date VARCHAR(10) NOT NULL, "
                + "points INT NOT NULL, "
                + "FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE) ENGINE=InnoDB");

        st.execute("CREATE TABLE IF NOT EXISTS quizzes ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "class_id INT NOT NULL, "
                + "date VARCHAR(10) NOT NULL, "
                + "title VARCHAR(100) NOT NULL, "
                + "overall INT NOT NULL, "
                + "FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE) ENGINE=InnoDB");

        st.execute("CREATE TABLE IF NOT EXISTS quiz_scores ("
                + "quiz_id INT NOT NULL, "
                + "student_id INT NOT NULL, "
                + "score INT NOT NULL, "
                + "PRIMARY KEY (quiz_id, student_id), "
                + "FOREIGN KEY (quiz_id) REFERENCES quizzes(id) ON DELETE CASCADE, "
                + "FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE) ENGINE=InnoDB");
        st.close();
    }

    // closes the database connection
    public void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            showError(e);
        }
    }

    // ---------- small helper methods used by the methods below ----------

    private void showError(SQLException e) {
        InputHelper.show("Database error:\n" + e.getMessage());
    }

    // puts the values into the ? placeholders of a SQL command
    private void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    // runs an INSERT / UPDATE / DELETE command. returns true if it worked
    private boolean run(String sql, Object... params) {
        boolean ok = false;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            ps.executeUpdate();
            ok = true;
        } catch (SQLException e) {
            showError(e);
        }
        return ok;
    }

    // runs a SELECT and returns the first number of the first row (or defaultValue if no row)
    private int getInt(String sql, int defaultValue, Object... params) {
        int result = defaultValue;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            showError(e);
        }
        return result;
    }

    // runs a SELECT and returns the first column of every row as text
    private ArrayList<String> getStrings(String sql, Object... params) {
        ArrayList<String> list = new ArrayList<String>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            showError(e);
        }
        return list;
    }

    // ---------- class records ----------

    // returns the class record with this name, or null if there is none
    public ClassRecord findClass(String name) {
        ClassRecord found = null;
        for (ClassRecord c : getClasses()) {
            if (c.name.equalsIgnoreCase(name)) {
                found = c;
            }
        }
        return found;
    }

    // creates a class record and returns it (null if it could not be created)
    public ClassRecord createClass(String name) {
        ClassRecord created = null;
        if (run("INSERT INTO classes (name) VALUES (?)", name)) {
            created = findClass(name);
        }
        return created;
    }

    // returns every class record, sorted by name
    public ArrayList<ClassRecord> getClasses() {
        ArrayList<ClassRecord> list = new ArrayList<ClassRecord>();
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT id, name FROM classes ORDER BY name")) {
            while (rs.next()) {
                list.add(new ClassRecord(rs.getInt(1), rs.getString(2)));
            }
        } catch (SQLException e) {
            showError(e);
        }
        return list;
    }

    // deletes a class record; its students, attendance, points and quizzes go with it (CASCADE)
    public boolean deleteClass(int classId) {
        return run("DELETE FROM classes WHERE id = ?", classId);
    }

    // ---------- students ----------

    // returns the students of a class, sorted alphabetically by last name (then first, then middle)
    public ArrayList<Student> getStudents(int classId) {
        ArrayList<Student> list = new ArrayList<Student>();
        String sql = "SELECT s.id, s.last_name, s.first_name, s.middle_name, "
                + "(SELECT COALESCE(SUM(p.points), 0) FROM points_log p WHERE p.student_id = s.id) "
                + "FROM students s WHERE s.class_id = ? "
                + "ORDER BY s.last_name, s.first_name, s.middle_name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, classId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Student(rs.getInt(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getInt(5)));
                }
            }
        } catch (SQLException e) {
            showError(e);
        }
        return list;
    }

    public int countStudents(int classId) {
        return getInt("SELECT COUNT(*) FROM students WHERE class_id = ?", 0, classId);
    }

    // true if another student of this class already has exactly this name
    // (excludeId is the student being edited, use -1 when adding)
    private boolean studentExists(int classId, String last, String first, String middle, int excludeId) {
        int count = getInt("SELECT COUNT(*) FROM students WHERE class_id = ? AND last_name = ? "
                + "AND first_name = ? AND middle_name = ? AND id <> ?",
                0, classId, last, first, middle, excludeId);
        return count > 0;
    }

    // adds a student. returns false if the same name is already in the class
    public boolean addStudent(int classId, String last, String first, String middle) {
        boolean added = false;
        if (!studentExists(classId, last, first, middle, -1)) {
            added = run("INSERT INTO students (class_id, last_name, first_name, middle_name) "
                    + "VALUES (?, ?, ?, ?)", classId, last, first, middle);
        }
        return added;
    }

    // changes the name of a student. returns false if the new name belongs to another student
    public boolean updateStudent(int studentId, int classId, String last, String first, String middle) {
        boolean updated = false;
        if (!studentExists(classId, last, first, middle, studentId)) {
            updated = run("UPDATE students SET last_name = ?, first_name = ?, middle_name = ? "
                    + "WHERE id = ?", last, first, middle, studentId);
        }
        return updated;
    }

    // removes a student (and their attendance, points and quiz scores)
    public boolean removeStudent(int studentId) {
        return run("DELETE FROM students WHERE id = ?", studentId);
    }

    // ---------- attendance ----------

    // saves "Present", "Absent" or "Late" for a student on a date
    // (replaces an older mark; REPLACE INTO is MySQL's way)
    public boolean setAttendance(int studentId, String date, String status) {
        return run("REPLACE INTO attendance (student_id, date, status) VALUES (?, ?, ?)",
                studentId, date, status);
    }

    // returns "Present", "Absent", "Late", or null if the student was not marked on that date
    public String getStatus(int studentId, String date) {
        ArrayList<String> result = getStrings(
                "SELECT status FROM attendance WHERE student_id = ? AND date = ?", studentId, date);
        String status = null;
        if (result.size() > 0) {
            status = result.get(0);
        }
        return status;
    }

    // true if at least one student of the class was marked on that date
    public boolean attendanceTaken(int classId, String date) {
        int count = getInt("SELECT COUNT(*) FROM attendance a JOIN students s ON s.id = a.student_id "
                + "WHERE s.class_id = ? AND a.date = ?", 0, classId, date);
        return count > 0;
    }

    // how many days the student has this status ("Present", "Absent" or "Late")
    public int countStatus(int studentId, String status) {
        return getInt("SELECT COUNT(*) FROM attendance WHERE student_id = ? AND status = ?",
                0, studentId, status);
    }

    // ---------- points ----------

    public boolean addPoints(int studentId, String date, int points) {
        return run("INSERT INTO points_log (student_id, date, points) VALUES (?, ?, ?)",
                studentId, date, points);
    }

    // points the student earned on one date
    public int getPointsOn(int studentId, String date) {
        return getInt("SELECT COALESCE(SUM(points), 0) FROM points_log WHERE student_id = ? AND date = ?",
                0, studentId, date);
    }

    // ---------- quizzes ----------

    // saves a new quiz and returns its id (-1 if it failed)
    public int createQuiz(int classId, String date, String title, int overall) {
        int id = -1;
        if (run("INSERT INTO quizzes (class_id, date, title, overall) VALUES (?, ?, ?, ?)",
                classId, date, title, overall)) {
            id = getInt("SELECT LAST_INSERT_ID()", -1);
        }
        return id;
    }

    public boolean saveScore(int quizId, int studentId, int score) {
        return run("REPLACE INTO quiz_scores (quiz_id, student_id, score) VALUES (?, ?, ?)",
                quizId, studentId, score);
    }

    // score of a student in a quiz, -1 if the student has no score for that quiz
    public int getScore(int quizId, int studentId) {
        return getInt("SELECT score FROM quiz_scores WHERE quiz_id = ? AND student_id = ?",
                -1, quizId, studentId);
    }

    public int countQuizzes(int classId) {
        return getInt("SELECT COUNT(*) FROM quizzes WHERE class_id = ?", 0, classId);
    }

    // returns the quizzes recorded on one date
    public ArrayList<Quiz> getQuizzesOn(int classId, String date) {
        ArrayList<Quiz> list = new ArrayList<Quiz>();
        String sql = "SELECT id, date, title, overall FROM quizzes WHERE class_id = ? AND date = ? ORDER BY id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, classId);
            ps.setString(2, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Quiz(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4)));
                }
            }
        } catch (SQLException e) {
            showError(e);
        }
        return list;
    }

    // total of all the quiz scores of a student
    public int getQuizScoreTotal(int studentId) {
        return getInt("SELECT COALESCE(SUM(score), 0) FROM quiz_scores WHERE student_id = ?", 0, studentId);
    }

    // total of the overall scores of the quizzes the student has a score in
    public int getQuizMaxTotal(int studentId) {
        return getInt("SELECT COALESCE(SUM(q.overall), 0) FROM quiz_scores qs "
                + "JOIN quizzes q ON q.id = qs.quiz_id WHERE qs.student_id = ?", 0, studentId);
    }

    // ---------- records ----------

    // every date that has attendance, points or a quiz for the class (newest first)
    public ArrayList<String> getRecordedDates(int classId) {
        return getStrings(
                "SELECT a.date FROM attendance a JOIN students s ON s.id = a.student_id WHERE s.class_id = ? "
                + "UNION SELECT q.date FROM quizzes q WHERE q.class_id = ? "
                + "UNION SELECT p.date FROM points_log p JOIN students s ON s.id = p.student_id WHERE s.class_id = ? "
                + "ORDER BY 1 DESC", classId, classId, classId);
    }
}

/* the TABLE (inventory style) window for the class list.
 * the user can add, edit and remove students; the list is always sorted
 * alphabetically by last name because it is reloaded from the database. */
class StudentEditor extends JDialog implements ActionListener {

    private Database db;
    private ClassRecord cr;
    private ArrayList<Student> students; // same order as the rows of the table
    private DefaultTableModel model;
    private JTable table;
    private JLabel totalLabel;

    public StudentEditor(Database db, ClassRecord cr) {
        super((Frame) null, "Class List - " + cr.name, true);
        this.db = db;
        this.cr = cr;

        // the table
        String[] headers = {"No.", "Last Name", "First Name", "Middle Name"};
        model = new DefaultTableModel(headers, 0) {
            public boolean isCellEditable(int row, int column) {
                return false; // editing is done with the Edit button, not inside the cells
            }
        };
        table = new JTable(model);
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(560, 320));

        // the top bar: class name on the left, total on the right
        totalLabel = new JLabel();
        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        top.add(new JLabel("Program and Block: " + cr.name), BorderLayout.WEST);
        top.add(totalLabel, BorderLayout.EAST);

        // the buttons at the bottom
        JPanel buttons = new JPanel(new FlowLayout());
        buttons.add(makeButton("Add Student", "ADD"));
        buttons.add(makeButton("Edit Student", "EDIT"));
        buttons.add(makeButton("Remove Student", "REMOVE"));
        buttons.add(makeButton("Done", "DONE"));

        setLayout(new BorderLayout(8, 8));
        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);

        reload();
    }

    // creates a button that reports to this window when clicked
    private JButton makeButton(String text, String command) {
        JButton button = new JButton(text);
        button.setActionCommand(command);
        button.addActionListener(this);
        return button;
    }

    // shows the window and waits until the user clicks Done
    public void open() {
        setVisible(true);
    }

    // reloads the students from the database (already sorted) and refreshes the table
    private void reload() {
        students = db.getStudents(cr.id);
        model.setRowCount(0);
        for (int i = 0; i < students.size(); i++) {
            Student s = students.get(i);
            model.addRow(new Object[]{i + 1, s.lastName, s.firstName, s.middleName});
        }
        totalLabel.setText("Total students: " + students.size());
    }

    // runs when any button is clicked
    public void actionPerformed(ActionEvent e) {
        String command = e.getActionCommand();
        if (command.equals("ADD")) {
            addStudent();
        } else if (command.equals("EDIT")) {
            editStudent();
        } else if (command.equals("REMOVE")) {
            removeStudent();
        } else {
            dispose(); // DONE
        }
    }

    // builds the form with the last name, first name and middle name boxes
    private JPanel buildForm(JTextField lastField, JTextField firstField, JTextField middleField) {
        JPanel panel = new JPanel(new GridLayout(3, 2, 6, 6));
        panel.add(new JLabel("Last Name:"));
        panel.add(lastField);
        panel.add(new JLabel("First Name:"));
        panel.add(firstField);
        panel.add(new JLabel("Middle Name (if available):"));
        panel.add(middleField);
        return panel;
    }

    /* asks for the name of a new student. "Save and Add Another" keeps the form
     * coming back so a whole class can be typed in quickly. */
    private void addStudent() {
        String last = "";
        String first = "";
        String middle = "";
        boolean again = true;

        while (again) {
            JTextField lastField = new JTextField(last, 15);
            JTextField firstField = new JTextField(first, 15);
            JTextField middleField = new JTextField(middle, 15);
            String[] buttons = {"Save and Add Another", "Save", "Cancel"};

            int pick = JOptionPane.showOptionDialog(this, buildForm(lastField, firstField, middleField),
                    "Add Student", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                    null, buttons, buttons[0]);

            if (pick != 0 && pick != 1) {
                again = false; // Cancel or window closed
            } else {
                last = InputHelper.properCase(lastField.getText());
                first = InputHelper.properCase(firstField.getText());
                middle = InputHelper.properCase(middleField.getText());

                if (last.length() == 0 || first.length() == 0) {
                    InputHelper.show(this, "Last name and first name are required.\n(Middle name is optional.)");
                } else if (!db.addStudent(cr.id, last, first, middle)) {
                    InputHelper.show(this, "This student is already in the class list.");
                } else {
                    reload();
                    last = "";
                    first = "";
                    middle = "";
                    if (pick == 1) {
                        again = false; // plain Save: stop after this student
                    }
                }
            }
        }
    }

    // edits the student selected in the table
    private void editStudent() {
        int row = table.getSelectedRow();
        if (row == -1) {
            InputHelper.show(this, "Select a student in the table first.");
        } else {
            Student s = students.get(row);
            String last = s.lastName;
            String first = s.firstName;
            String middle = s.middleName;
            boolean again = true;

            while (again) {
                JTextField lastField = new JTextField(last, 15);
                JTextField firstField = new JTextField(first, 15);
                JTextField middleField = new JTextField(middle, 15);
                String[] buttons = {"Save", "Cancel"};

                int pick = JOptionPane.showOptionDialog(this, buildForm(lastField, firstField, middleField),
                        "Edit Student", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                        null, buttons, buttons[0]);

                if (pick != 0) {
                    again = false;
                } else {
                    last = InputHelper.properCase(lastField.getText());
                    first = InputHelper.properCase(firstField.getText());
                    middle = InputHelper.properCase(middleField.getText());

                    if (last.length() == 0 || first.length() == 0) {
                        InputHelper.show(this, "Last name and first name are required.\n(Middle name is optional.)");
                    } else if (!db.updateStudent(s.id, cr.id, last, first, middle)) {
                        InputHelper.show(this, "Another student already has that name.");
                    } else {
                        reload(); // the list is sorted again, so the student may move
                        again = false;
                    }
                }
            }
        }
    }

    // removes the student selected in the table (after asking YES / NO)
    private void removeStudent() {
        int row = table.getSelectedRow();
        if (row == -1) {
            InputHelper.show(this, "Select a student in the table first.");
        } else {
            Student s = students.get(row);
            boolean sure = InputHelper.askYesNo(this, "Remove " + s.getFullName() + " from the class list?\n"
                    + "Their attendance, points and quiz scores will also be deleted.");
            if (sure) {
                db.removeStudent(s.id);
                reload();
            }
        }
    }
}

/* marks the students PRESENT or ABSENT for today, based on the class list.
 * marking again on the same day replaces the earlier marks.
 * a second check can then change the absent students to LATE. */
class AttendanceManager {

    private Database db;
    private int checkCount = 0; // number of attendance checks done in this class menu

    public AttendanceManager(Database db) {
        this.db = db;
    }

    // true if the status means the student came to class (Present or Late)
    // other classes (Recitation, QuizManager) use this so Late is treated like Present
    public static boolean isHere(String status) {
        return "Present".equals(status) || "Late".equals(status);
    }

    // goes through the class list one student at a time with Present / Absent buttons
    public void mark(ClassRecord cr) {
        checkCount++;

        ArrayList<Student> students = db.getStudents(cr.id);
        String today = InputHelper.today();
        String[] options = {"Present", "Absent", "Stop"};

        int i = 0;
        boolean stop = false;
        while (i < students.size() && !stop) {
            Student s = students.get(i);
            String current = db.getStatus(s.id, today);
            String note = "not marked yet";
            if (current != null) {
                note = current;
            }

            int pick = InputHelper.choose("ATTENDANCE - " + cr.name + " (Check " + checkCount + ")",
                    "Date: " + InputHelper.prettyDate(today) + "\n\n"
                    + "Student " + (i + 1) + " of " + students.size() + ":\n"
                    + s.getFullName() + "\n(Currently: " + note + ")", options);

            if (pick == 0) {
                db.setAttendance(s.id, today, "Present");
                i++;
            } else if (pick == 1) {
                db.setAttendance(s.id, today, "Absent");
                i++;
            } else {
                stop = true; // Stop button or window closed
            }
        }
        showSummary(cr, today);
    }

    /* the second check: goes through ONLY the students marked Absent, one at a time,
     * so each one can stay Absent or be changed to Late. */
    public void markAbsentOrLate(ClassRecord cr) {
        ArrayList<Student> students = db.getStudents(cr.id);
        String today = InputHelper.today();

        // keep only the students marked Absent in the first check
        ArrayList<Student> absentStudents = new ArrayList<Student>();
        for (Student s : students) {
            if ("Absent".equals(db.getStatus(s.id, today))) {
                absentStudents.add(s);
            }
        }

        if (absentStudents.size() == 0) {
            InputHelper.show("No students are currently marked as absent.\n"
                    + "No second attendance check needed.");
        } else {
            checkCount++; // counted only when a check really happens
            String[] options = {"Absent", "Late", "Stop"};

            int i = 0;
            boolean stop = false;
            while (i < absentStudents.size() && !stop) {
                Student s = absentStudents.get(i);

                int pick = InputHelper.choose("ATTENDANCE - " + cr.name + " (Check " + checkCount + ")",
                        "Date: " + InputHelper.prettyDate(today) + "\n\n"
                        + "Student " + (i + 1) + " of " + absentStudents.size() + ":\n"
                        + s.getFullName() + "\n(Currently: Absent)", options);

                if (pick == 0) {
                    i++; // stays Absent, nothing to save
                } else if (pick == 1) {
                    db.setAttendance(s.id, today, "Late");
                    i++;
                } else {
                    stop = true; // Stop button or window closed
                }
            }
            showSummary(cr, today);
        }
    }

    // shows today's attendance of the whole class in a table
    private void showSummary(ClassRecord cr, String date) {
        ArrayList<Student> students = db.getStudents(cr.id);
        String[][] rows = new String[students.size()][3];
        int present = 0;
        int late = 0;
        int absent = 0;

        for (int i = 0; i < students.size(); i++) {
            Student s = students.get(i);
            String status = db.getStatus(s.id, date);
            if (status == null) {
                status = "Not marked";
            } else if (status.equals("Present")) {
                present++;
            } else if (status.equals("Late")) {
                late++;
            } else {
                absent++;
            }
            rows[i] = new String[]{"" + (i + 1), s.getFullName(), status};
        }

        InputHelper.showTable("ATTENDANCE\nProgram and Block: " + cr.name + "\nDate: "
                + InputHelper.prettyDate(date) + "\nPresent: " + present + "   Late: " + late
                + "   Absent: " + absent
                + "   Not marked: " + (students.size() - present - late - absent),
                new String[]{"No.", "Student", "Status"}, rows);
    }
}

/*recitation randomizer uses a separate pool taken from the class list,
 * so removing a student from the pool never deletes them from the class list.
 * the pool holds the students marked PRESENT or LATE today (or the whole class if
 * attendance was not taken and the user agrees).*/
class Recitation {

    private Database db;
    private ClassRecord cr;
    private ArrayList<Student> pool;     // students that can still be picked
    private ArrayList<Student> removed;  // students removed from the pool
    private Random random;
    private boolean built;               // true when the pool has been filled
    private boolean useAll;              // true when the whole class is used

    // sets up an empty pool and a random number generator
    public Recitation(Database db, ClassRecord cr) {
        this.db = db;
        this.cr = cr;
        this.random = new Random();
        this.pool = new ArrayList<Student>();
        this.removed = new ArrayList<Student>();
        this.built = false;
        this.useAll = false;
    }

    // forgets the pool, so it is rebuilt the next time the randomizer starts
    public void reset() {
        built = false;
    }

    // fills the pool from the database
    private void fillPool() {
        pool.clear();
        removed.clear();
        String today = InputHelper.today();
        for (Student s : db.getStudents(cr.id)) {
            if (useAll || AttendanceManager.isHere(db.getStatus(s.id, today))) {
                pool.add(s);
            }
        }
        built = true;
    }

    /* picks a random student from the pool, asks whether to remove that student
     * from the pool, and repeats while the user wants to pick another */
    public void start() {
        if (!built) {
            useAll = false;
            fillPool();
            if (pool.size() == 0) {
                boolean wholeClass = InputHelper.askYesNo("No students are marked present or late today.\n"
                        + "Use the whole class list instead?");
                if (wholeClass) {
                    useAll = true;
                    fillPool();
                }
            }
            if (pool.size() == 0) {
                built = false; // nothing to pick from
            }
        }

        boolean again = built;
        while (again) {
            boolean canPick = true;

            if (pool.size() == 0) {
                boolean refill = InputHelper.askYesNo("The pool is empty.\n"
                        + "Put all students back in the randomizer?");
                if (refill) {
                    fillPool();
                } else {
                    canPick = false;
                    again = false;
                }
            }

            if (canPick) {
                int index = random.nextInt(pool.size());
                Student picked = pool.get(index);
                InputHelper.show("RECITATION RANDOMIZER\n\nSelected Student:\n\n" + picked.getFullName());

                boolean remove = InputHelper.askYesNo("Remove " + picked.getFullName() + " from the randomizer?");
                if (remove) {
                    pool.remove(index);   // removed from the pool only, NOT from the class list
                    removed.add(picked);
                }

                again = InputHelper.askYesNo("Pick another student?\n(Students left in pool: "
                        + pool.size() + ")");
            }
        }
    }
}

/* manage points: add points to a student and view everyone's points.
 * every addition is saved with its date, so daily and overall totals are possible. */
class PointsManager {

    private Database db;

    public PointsManager(Database db) {
        this.db = db;
    }

    // shows the Manage Points buttons until the user chooses Back
    public void run(ClassRecord cr) {
        String[] options = {"Add Points", "View Points", "Back"};
        int choice = 0;
        do {
            choice = InputHelper.choose("MANAGE POINTS - " + cr.name, "Choose an option:", options);
            if (choice == 0) {
                addPoints(cr);
            } else if (choice == 1) {
                viewPoints(cr);
            }
        } while (choice == 0 || choice == 1);
    }

    // picks a student from a list, asks for the points, then saves them under today's date
    private void addPoints(ClassRecord cr) {
        ArrayList<Student> students = db.getStudents(cr.id);
        int index = InputHelper.pickIndex("ADD POINTS", "Select a student:", InputHelper.namesOf(students));
        if (index != -1) {
            Student s = students.get(index);
            int pts = InputHelper.readInt("ADD POINTS\n\nEnter points for " + s.getFullName()
                    + ":\n(Use a negative number to deduct)");
            if (pts != InputHelper.CANCELLED) {
                if (db.addPoints(s.id, InputHelper.today(), pts)) {
                    s.points += pts;
                    InputHelper.show(s.getInfo()); // overridden getInfo(): name and points
                }
            }
        }
    }

    // shows every student with today's points and total points
    private void viewPoints(ClassRecord cr) {
        ArrayList<Student> students = db.getStudents(cr.id);
        String today = InputHelper.today();
        String[][] rows = new String[students.size()][4];
        for (int i = 0; i < students.size(); i++) {
            Student s = students.get(i);
            rows[i] = new String[]{"" + (i + 1), s.getFullName(),
                "" + db.getPointsOn(s.id, today), "" + s.points};
        }
        InputHelper.showTable("VIEW POINTS\nProgram and Block: " + cr.name,
                new String[]{"No.", "Student", "Points Today", "Total Points"}, rows);
    }
}

/* record quiz scores
 * the overall (maximum) score is entered ONCE per quiz, then only PRESENT or LATE students
 * are scored; absent students automatically get 0. Many quizzes can be saved. */
class QuizManager {

    private Database db;

    public QuizManager(Database db) {
        this.db = db;
    }

    /* asks for the quiz title and overall score, then for a score for each student who came to class.
     * nothing is saved if the user cancels. */
    public void record(ClassRecord cr) {
        ArrayList<Student> students = db.getStudents(cr.id);
        String today = InputHelper.today();

        int presentCount = 0;
        for (Student s : students) {
            if (AttendanceManager.isHere(db.getStatus(s.id, today))) {
                presentCount++;
            }
        }

        if (!db.attendanceTaken(cr.id, today)) {
            InputHelper.show("Please mark today's attendance first.\nOnly present students take the quiz.");
        } else if (presentCount == 0) {
            InputHelper.show("No students are marked present or late today.");
        } else {
            boolean cancelled = false;

            // the quiz title (optional)
            String title = InputHelper.readText("QUIZ SCORE\n\nEnter quiz title:\n(Leave blank for the default name)");
            if (title == null) {
                cancelled = true;
            } else if (title.length() == 0) {
                title = "Quiz " + (db.countQuizzes(cr.id) + 1);
            }

            // the overall score is entered only once
            int overall = 0;
            while (overall <= 0 && !cancelled) {
                overall = InputHelper.readInt("QUIZ SCORE - " + title + "\n\nEnter Overall Quiz Score:");
                if (overall == InputHelper.CANCELLED) {
                    cancelled = true;
                } else if (overall <= 0) {
                    InputHelper.show("Overall score must be greater than 0.");
                }
            }

            // absent students get 0; present and late students are asked for a score
            int[] scores = new int[students.size()];
            int i = 0;
            while (i < students.size() && !cancelled) {
                Student s = students.get(i);
                if (!AttendanceManager.isHere(db.getStatus(s.id, today))) {
                    scores[i] = 0;
                } else {
                    int score = -1;
                    while ((score < 0 || score > overall) && !cancelled) {
                        score = InputHelper.readInt("QUIZ SCORE - " + title + "\nOverall Score: " + overall
                                + "\n\n" + s.getFullName() + " - Enter score:");
                        if (score == InputHelper.CANCELLED) {
                            cancelled = true;
                        } else if (score < 0 || score > overall) {
                            InputHelper.show("Score must be from 0 to " + overall + ".");
                        }
                    }
                    if (!cancelled) {
                        scores[i] = score;
                    }
                }
                i++;
            }

            // save everything only when all the scores were entered
            if (!cancelled) {
                int quizId = db.createQuiz(cr.id, today, title, overall);
                if (quizId != -1) {
                    for (int k = 0; k < students.size(); k++) {
                        db.saveScore(quizId, students.get(k).id, scores[k]);
                    }
                    InputHelper.show("\"" + title + "\" recorded.");
                }
            }
        }
    }
}

// view the record of ONE DAY (today or any date that has records) or the OVERALL record
class RecordViewer {

    private Database db;
    private FinalRecord finalRecord;

    public RecordViewer(Database db, FinalRecord finalRecord) {
        this.db = db;
        this.finalRecord = finalRecord;
    }

    // shows the View Record buttons until the user chooses Back
    public void run(ClassRecord cr) {
        String[] options = {"Today's Record", "Record of Another Date", "Overall Record", "Back"};
        int choice = 0;
        do {
            choice = InputHelper.choose("VIEW RECORD - " + cr.name, "Choose an option:", options);
            if (choice == 0) {
                showDay(cr, InputHelper.today());
            } else if (choice == 1) {
                pickDate(cr);
            } else if (choice == 2) {
                finalRecord.showOverall(cr);
            }
        } while (choice >= 0 && choice <= 2);
    }

    // lets the user pick one of the dates that have records
    private void pickDate(ClassRecord cr) {
        ArrayList<String> dates = db.getRecordedDates(cr.id);
        if (dates.size() == 0) {
            InputHelper.show("There are no records yet.");
        } else {
            String[] labels = new String[dates.size()];
            for (int i = 0; i < dates.size(); i++) {
                labels[i] = InputHelper.prettyDate(dates.get(i));
            }
            int index = InputHelper.pickIndex("RECORD OF ANOTHER DATE", "Select a date:", labels);
            if (index != -1) {
                showDay(cr, dates.get(index));
            }
        }
    }

    // shows attendance, points and quiz scores of every student for one date
    private void showDay(ClassRecord cr, String date) {
        ArrayList<Student> students = db.getStudents(cr.id);
        ArrayList<Quiz> quizzes = db.getQuizzesOn(cr.id, date);
        String[][] rows = new String[students.size()][5];

        for (int i = 0; i < students.size(); i++) {
            Student s = students.get(i);

            String status = db.getStatus(s.id, date);
            if (status == null) {
                status = "Not marked";
            }

            String quizText = "";
            for (Quiz q : quizzes) {
                int score = db.getScore(q.id, s.id);
                if (score != -1) {
                    if (quizText.length() > 0) {
                        quizText += ", ";
                    }
                    quizText += q.title + ": " + score + "/" + q.overall;
                }
            }
            if (quizText.length() == 0) {
                quizText = "-";
            }

            rows[i] = new String[]{"" + (i + 1), s.getFullName(), status,
                "" + db.getPointsOn(s.id, date), quizText};
        }

        InputHelper.showTable("RECORD FOR THE DAY\nProgram and Block: " + cr.name + "\nDate: "
                + InputHelper.prettyDate(date),
                new String[]{"No.", "Student", "Attendance", "Points", "Quiz Scores"}, rows);
    }
}

/* generates the Final Class Record from the data saved in the database
 * (attendance, points and quizzes of every student). It can also be saved as a CSV file
 * that opens in Excel. */
class FinalRecord {

    private Database db;

    public FinalRecord(Database db) {
        this.db = db;
    }

    private String[] headers() {
        return new String[]{"No.", "Student", "Days Present", "Days Late", "Days Absent",
            "Attendance %", "Total Points", "Quiz Total", "Quiz %"};
    }

    // builds one row of text per student
    private String[][] buildRows(ClassRecord cr) {
        ArrayList<Student> students = db.getStudents(cr.id);
        String[][] rows = new String[students.size()][9];

        for (int i = 0; i < students.size(); i++) {
            Student s = students.get(i);

            int present = db.countStatus(s.id, "Present");
            int late = db.countStatus(s.id, "Late");
            int absent = db.countStatus(s.id, "Absent");
            int sessions = present + late + absent;
            String attendancePct = "N/A";
            if (sessions > 0) {
                // a late student still came to class, so Late counts as attending
                attendancePct = String.format("%.1f%%", (present + late) * 100.0 / sessions);
            }

            int quizScore = db.getQuizScoreTotal(s.id);
            int quizMax = db.getQuizMaxTotal(s.id);
            String quizTotal = "N/A";
            String quizPct = "N/A";
            if (quizMax > 0) {
                quizTotal = quizScore + "/" + quizMax;
                quizPct = String.format("%.1f%%", quizScore * 100.0 / quizMax);
            }

            rows[i] = new String[]{"" + (i + 1), s.getFullName(), "" + present, "" + late,
                "" + absent, attendancePct, "" + s.points, quizTotal, quizPct};
        }
        return rows;
    }

    // shows the overall record in a table
    public void showOverall(ClassRecord cr) {
        InputHelper.showTable("OVERALL RECORD\nProgram and Block: " + cr.name,
                headers(), buildRows(cr));
    }

    // shows the final class record, then offers to save it as a CSV file
    public void generate(ClassRecord cr) {
        String[][] rows = buildRows(cr);
        InputHelper.showTable("FINAL CLASS RECORD\nProgram and Block: " + cr.name + "\nGenerated: "
                + InputHelper.prettyDate(InputHelper.today()), headers(), rows);

        if (InputHelper.askYesNo("Save the final class record as a CSV file?")) {
            saveCsv(cr, rows);
        }
    }

    // writes the record to a file named like BSIT_1A_FinalRecord.csv
    private void saveCsv(ClassRecord cr, String[][] rows) {
        String fileName = cr.name.replaceAll("[^A-Za-z0-9]", "_") + "_FinalRecord.csv";
        try (PrintWriter out = new PrintWriter(fileName)) {
            out.println(csvLine(headers()));
            for (String[] row : rows) {
                out.println(csvLine(row));
            }
            InputHelper.show("Saved to:\n" + new File(fileName).getAbsolutePath());
        } catch (FileNotFoundException e) {
            InputHelper.show("Could not save the file:\n" + e.getMessage());
        }
    }

    // joins the values with commas; each value is quoted because names contain commas
    private String csvLine(String[] values) {
        String line = "";
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                line += ",";
            }
            line += "\"" + values[i].replace("\"", "\"\"") + "\"";
        }
        return line;
    }
}