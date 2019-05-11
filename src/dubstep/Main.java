package dubstep;

import dubstep.executor.BaseNode;
import dubstep.executor.DeleteManager;
import dubstep.planner.PlanTree;
import dubstep.storage.TableManager;
import dubstep.storage.datatypes;
import dubstep.utils.Explainer;
import dubstep.utils.QueryTimer;
import dubstep.utils.Tuple;
import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParser;
import net.sf.jsqlparser.parser.ParseException;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.Union;

import java.io.*;
import java.sql.Date;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static dubstep.storage.datatypes.DATE_TYPE;

public class Main {
    public static final String PROMPT = "$>";
    public static TableManager mySchema = new TableManager();
    // Globals used across project
    static public int maxThread = 1;
    static public boolean DEBUG_MODE = false; // will print out logs - all logs should be routed through this flag
    static public boolean EXPLAIN_MODE = false; // will print statistics of the code
    static ArrayList<Integer> dateSet;
    static boolean create = true;

    public static void main(String[] args) throws ParseException, SQLException {
        //Get all command line arguments
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--in-mem"))
                mySchema.setInMem(true);
            if (args[i].equals("--on-disk"))
                mySchema.setInMem(false);
        }
        mySchema.setInMem(true);

        Scanner scanner = new Scanner(System.in);
        QueryTimer timer = new QueryTimer();

        try {
            String line = null;
            BufferedReader reader = null;
            reader = new BufferedReader(new FileReader("tables"));
            if (reader == null)
                line = null;
            else
                line = reader.readLine();

            while (line != null) {
                CCJSqlParser parser = new CCJSqlParser(new StringReader(line));
                Statement query = parser.Statement();
                CreateTable createQuery = (CreateTable) query;
                if (!mySchema.createTable(createQuery)) {
                }
                line = reader.readLine();

            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println(PROMPT);
        while (scanner.hasNext()) {

            String sqlString = scanner.nextLine();

            while (sqlString.indexOf(';') < 0)
                sqlString = sqlString + " " + scanner.nextLine();
            executeQuery(sqlString);

        }
    }

    private static void executeQuery(String sqlString) throws ParseException, SQLException {

        QueryTimer timer = new QueryTimer();
        CCJSqlParser parser = new CCJSqlParser(new StringReader(sqlString));
        Statement query = parser.Statement();

        timer.reset();
        timer.start();

        if (query instanceof CreateTable) {
            CreateTable createQuery = (CreateTable) query;
            BufferedWriter table_file = null;
            create = false;

            if (!mySchema.createTable(createQuery)) {
                System.out.println("Unable to create DubTable - DubTable already exists");
            } else {
                try {
                    table_file = new BufferedWriter(new FileWriter("tables", true));

                    table_file.write(sqlString + "\n");
                    table_file.close();
                } catch (IOException e) {
                    e.printStackTrace();

                }

            }
        } else if (query instanceof Select) {

            Select selectQuery = (Select) query;
            SelectBody selectBody = selectQuery.getSelectBody();
            BaseNode root;
            if (selectBody instanceof PlainSelect) {
                root = PlanTree.generatePlan((PlainSelect) selectBody);
                if (EXPLAIN_MODE) {
                    Explainer e1 = new Explainer(root);
                    e1.explain();
                }

                root = PlanTree.optimizePlan(root);
                root.initProjPushDownInfo();
                if (EXPLAIN_MODE) {
                    Explainer e1 = new Explainer(root);
                    e1.explain();
                }
            } else {
                root = PlanTree.generateUnionPlan((Union) selectBody);
            }
            Tuple tuple = root.getNextTuple();

            while (tuple != null) {
                replaceDate(tuple, root.typeList);
                String t1 = tuple.getProjection();
                System.out.println(t1);

                tuple = root.getNextTuple();
            }

            if (EXPLAIN_MODE) {
                Explainer explainer = new Explainer(root);
                explainer.explain();
            }
        } else if (query instanceof Delete) {
            Delete deleteQuery = (Delete) query;
            DeleteManager.delete(deleteQuery.getTable(), deleteQuery.getWhere());
        } else {
            throw new java.sql.SQLException("I can't understand " + sqlString);
        }
        timer.stop();
        if (DEBUG_MODE)
            System.out.println("Execution time = " + timer.getTotalTime());
        timer.reset();
        System.out.println(PROMPT);
    }

    static private void replaceDate(Tuple tuple, List<datatypes> typeList) {

        for (int i = 0; i < typeList.size(); i++) {
            if (typeList.get(i) == DATE_TYPE) {
                LongValue val = (LongValue) tuple.valueArray[i];
                Date date = new Date(val.getValue());
                DateValue dval = new DateValue(date.toString());
                tuple.valueArray[i] = dval;
            }
        }
    }

}
