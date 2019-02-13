package dubstep.storage;

import dubstep.utils.tuple;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import sun.misc.Lock;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class table {

    String tableName;
    ArrayList<ColumnDefinition> columnDefinitions;
    String dataFile;
    Lock tableLock = new Lock();
    BufferedReader tableReader;
    Integer currentMaxTid;

    public table(CreateTable createTable) {
        tableName = createTable.getTable().getName();
        columnDefinitions = (ArrayList) createTable.getColumnDefinitions();
        dataFile = "data/" + tableName + ".dat";
    }

    void lockTable() {
        try {
            tableLock.lock();
        } catch (InterruptedException e) {
            System.out.println("unable to lock table " + this.tableName);
            e.printStackTrace();
        }
    }

    void unlockTable() {
        tableLock.unlock();
    }

    public boolean initRead() {
        try {

            this.tableReader = new BufferedReader(new FileReader(dataFile));
            this.currentMaxTid = 0;


        } catch (FileNotFoundException e) {
            System.out.println("datafile node found" + this.dataFile);

        }
        return (tableReader == null);

    }

    public boolean readTuples(int tupleCount, ArrayList<tuple> tupleBuffer) {

        int readTuples = 0;
        int TidStart = this.currentMaxTid;

        ArrayList<String> fileBuffer = new ArrayList<>();

        this.lockTable();


        if (this.tableReader == null) {
            System.out.println("Stop !! - Table read not initialized or table read already complete");
            this.unlockTable();
            return false;
        } else {

            try {
                String line = "";

                while (tupleCount > readTuples && (line = this.tableReader.readLine()) != null) {
                    fileBuffer.add(line);
                    readTuples++;
                }
                this.currentMaxTid += readTuples;
                if (readTuples != tupleCount) {
                    this.tableReader.close();
                    this.currentMaxTid = 0;
                }
                this.tableLock.unlock();
                tupleBuffer.clear();
                convertToTuples(tupleBuffer, fileBuffer, TidStart, tupleCount, readTuples);
                fileBuffer.clear();


            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return (readTuples == tupleCount);

    }

    void convertToTuples(ArrayList<tuple> tupleBuffer, ArrayList<String> fileBuffer, int TidStart, int tupleCount, int readTuples) {
        for (String tupleString : fileBuffer) {
            tupleBuffer.add(new tuple(tupleString, tupleCount++, this.columnDefinitions));

        }
    }

}
