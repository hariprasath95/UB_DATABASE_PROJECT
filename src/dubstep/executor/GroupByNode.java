package dubstep.executor;

import dubstep.Aggregate.Aggregate;
import dubstep.Main;
import dubstep.utils.Evaluator;
import dubstep.utils.Tuple;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.PrimitiveValue;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class GroupByNode extends BaseNode {
    private HashMap<String, ArrayList<AggregateMap>> buffer;
    private Evaluator evaluator;
    private ArrayList<SelectExpressionItem> selectExpressionItems;
    private ArrayList<Expression> selectExpressions;
    private Aggregate[] aggObjects;
    private Boolean isInit = false;
    private Tuple next;
    private List<ColumnDefinition> colDefs;
    private ArrayList<Integer> aggIndices;
    //private Boolean done;

    public GroupByNode(BaseNode innernode, ArrayList<SelectExpressionItem> selectExpressionItems) {
        this.innerNode = innernode;
        this.selectExpressionItems = selectExpressionItems;
        this.selectExpressions = null;
        this.aggObjects = null;
        this.aggIndices = new ArrayList<>();
        this.initProjectionInfo();
        this.evaluator = new Evaluator(this.innerNode.projectionInfo);
        this.next = null;
       // if (Main.mySchema.isInMem())
            this.fillBuffer();
        //else {
        //    this.generateSortNode();
        //}
    }

    public Tuple getNextRow() {

        //if (Main.mySchema.isInMem())
            return inMemNextRow();
        //else
        //    return onDiskNextRow();

    }

    public Tuple onDiskNextRow() {

        if (this.aggObjects == null){
            this.initAggObjects();
        }
        if (!this.isInit) {
            next = this.innerNode.getNextTuple();
            this.isInit = true;
        }

        while(next != null) {

            PrimitiveValue[] rowValues = new PrimitiveValue[selectExpressionItems.size()];
            this.evaluator.setTuple(next);
            for (int i = 0; i < selectExpressions.size(); i++) {
                if (selectExpressions.get(i) instanceof Column) {
                    try {
                        rowValues[i] = evaluator.eval(selectExpressions.get(i));
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }else {
                    rowValues[i] = aggObjects[i].yield(next);
                }
            }
        }

        next = this.innerNode.getNextTuple();
        return null;
    }

    public void initAggObjects() {

        aggObjects = new Aggregate[selectExpressionItems.size()];
        for(int i:aggIndices) {
            aggObjects[i] = Aggregate.getAggObject((Function) selectExpressions.get(i), evaluator);
        }
    }

    public Tuple inMemNextRow(){

        if (this.buffer == null) {
            return null;
        }
        if (this.buffer.isEmpty()) {
            this.buffer = null;
            return null;
        }
        String resString =  buffer.keySet().iterator().next();
        Tuple result = new Tuple(resString, -1, this.colDefs);
        ArrayList<AggregateMap> mapList = buffer.get(resString);
        buffer.remove(resString);

        for (AggregateMap map : mapList) {
            result.setValue(map.index, map.aggregate.getCurrentResult());
        }

        return (result);
    }

    private void fillBuffer() {

        this.buffer = new HashMap<String, ArrayList<AggregateMap>>();

        if (!this.isInit) {
            next = this.innerNode.getNextTuple();
            this.isInit = true;
        }
        this.colDefs = next.getColumnDefinitions();
        ArrayList<Expression> selectExpressions = new ArrayList<>();

        for (SelectExpressionItem expressionItems : selectExpressionItems) {
            selectExpressions.add(expressionItems.getExpression());
        }

        PrimitiveValue[] rowValues = new PrimitiveValue[selectExpressionItems.size()];
        Tuple keyRow = new Tuple(rowValues);

        for (int i = 0; i < selectExpressions.size(); i++) {
            if (! (selectExpressions.get(i) instanceof Column)) {
                this.aggIndices.add(i);
            }
        }

        while (next != null) {
            this.evaluator.setTuple(next);

            for (int i = 0; i < selectExpressions.size(); i++) {
                if (selectExpressions.get(i) instanceof Column) {
                    try {
                        keyRow.setValue(i, evaluator.eval(selectExpressions.get(i)));
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }

            String keyString = keyRow.toString();

            if (buffer.containsKey(keyString)) {
                for (AggregateMap pair : buffer.get(keyString)) {
                    pair.aggregate.yield(next);
                }
                next = this.innerNode.getNextTuple();
                continue;
            }

            ArrayList<AggregateMap> mapList = new ArrayList<AggregateMap>();
            Aggregate aggregate;

            for (int i : aggIndices) {
                aggregate = Aggregate.getAggObject((Function) selectExpressions.get(i), evaluator);
                aggregate.yield(next);
                AggregateMap map = new AggregateMap(i, aggregate);
                mapList.add(map);
            }
            buffer.put(keyString, mapList);
            next = this.innerNode.getNextTuple();
        }
    }

    public void generateSortNode() {

        Expression selectExpression;
        Column sortColumn = null;
        OrderByElement sortElement = new OrderByElement();

        for (SelectExpressionItem expressionItem : selectExpressionItems) {
            selectExpression = (expressionItem.getExpression());
            this.selectExpressions.add(selectExpression);
            if (selectExpression instanceof Column)
                sortColumn = (Column) selectExpression;
        }

        sortElement.setExpression(sortColumn);

        List<OrderByElement> elems = new ArrayList<>();
        elems.add(sortElement);

        this.innerNode = new SortNode(elems, this.innerNode);
    }

    @Override
    void initProjectionInfo() {
        projectionInfo = new ArrayList<>();
        for (SelectItem selectItem : selectExpressionItems) {
            String columnName = ((SelectExpressionItem) selectItem).getExpression().toString();
            String alias = ((SelectExpressionItem) selectItem).getAlias();
            if (alias == null) {
                projectionInfo.add(columnName);
            } else {
                projectionInfo.add(alias);
            }
        }
    }

    @Override
    void resetIterator() {
        this.innerNode.resetIterator();
        this.aggObjects = null;
        this.isInit = false;
        this.fillBuffer();
    }

    private class AggregateMap {
        private int index;
        private Aggregate aggregate;

        private AggregateMap(int index, Aggregate aggregate) {
            this.index = index;
            this.aggregate = aggregate;
        }
    }
}