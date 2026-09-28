import java.util.Date as UtilDate;
import java.sql.Date as SqlDate;

/**
 * The example from the README: two classes with the same simple name, used side by side.
 *
 * <p>{@code java.util.Date} and {@code java.sql.Date} both are called {@code Date}. With aliases they
 * can be used in the same file, and both still refer to the real JDK classes.
 */
public class DateAliasExample {

    private final UtilDate createdAt;
    private final SqlDate databaseDate;

    /**
     * Creates the example.
     *
     * @param millis point in time
     */
    public DateAliasExample(long millis) {
        this.createdAt = new UtilDate(millis);
        this.databaseDate = new SqlDate(millis);
    }

    /**
     * @return a description of the two values
     */
    public String describe() {
        return "UtilDate -> " + createdAt.getClass().getName() + " (" + createdAt.getTime() + ")\n"
                + "SqlDate  -> " + databaseDate.getClass().getName() + " (" + databaseDate.getTime() + ")";
    }

    /**
     * Runs the example.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        DateAliasExample example = new DateAliasExample(0L);
        System.out.println(example.describe());

        if (example.createdAt.getClass() != java.util.Date.class) {
            throw new IllegalStateException("UtilDate must resolve to java.util.Date");
        }
        if (example.databaseDate.getClass() != java.sql.Date.class) {
            throw new IllegalStateException("SqlDate must resolve to java.sql.Date");
        }
        System.out.println("DateAliasExample OK");
    }
}
