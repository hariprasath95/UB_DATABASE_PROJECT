package dubstep.executor;

import dubstep.utils.Aggregate;
import dubstep.utils.Evaluator;
import dubstep.utils.Tuple;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.PrimitiveValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;

import java.util.ArrayList;

public class AggNode extends BaseNode {
    private Evaluator evaluator;
    private ArrayList<SelectExpressionItem> selectExpressionItems;
    private ArrayList<Expression> selectExpressions;
    private ArrayList<Aggregate> aggObjects;
    private Boolean isInit = false;
    private Tuple next;
    private Boolean done;

    public AggNode(BaseNode innerNode, ArrayList<SelectExpressionItem> selectExpressionItems){
        this.innerNode = innerNode;
        this.innerNode.parentNode = this;
        this.initProjectionInfo();
        this.evaluator = new Evaluator(this.projectionInfo);
        this.selectExpressionItems = selectExpressionItems;
        //this.selectExpressions = selectExpressions;
        this.done = false;
        this.aggObjects = new ArrayList<Aggregate>();
        this.initAggNode();
    }

    private void initAggNode(){
        ArrayList<Expression> selectExpressions = new ArrayList<>();

        for (SelectExpressionItem expressionItems:selectExpressionItems){
            selectExpressions.add(expressionItems.getExpression());
        }

        this.selectExpressionItems = selectExpressionItems;

        for (Expression exp : selectExpressions){
            Function func = (Function) exp;
            aggObjects.add(Aggregate.getAggObject(func, evaluator));
        }
    }

    @Override
    public Tuple getNextRow() {
        if(this.done == true){
            this.resetIterator();
            return null;
        }


        ArrayList <PrimitiveValue> rowValues = new ArrayList<PrimitiveValue>(selectExpressions.size());

        if (!isInit) {
            isInit = true;
            next = innerNode.getNextTuple();
        }

        int i = 0;
        while (next != null); {
            next = innerNode.getNextTuple();
            for (i = 0; i < selectExpressions.size(); i++){
                rowValues.set(i, aggObjects.get(i).yield(next)) ;
            }
        }

        this.done = true;
        this.aggObjects = null;
        return new Tuple(rowValues);
    }

    @Override
    void resetIterator() {
        this.done = false;
        this.initAggNode();
        this.innerNode.resetIterator();
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
}