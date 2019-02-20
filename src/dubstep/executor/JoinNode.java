package dubstep.executor;

import dubstep.utils.Tuple;

import java.util.ArrayList;

public class JoinNode extends BaseNode {

    Tuple innerTuple;
    Boolean initJoin = false;
    public JoinNode(BaseNode innerNode,BaseNode outerNode)
    {
        this.innerNode = innerNode;
        this.outerNode = outerNode;
        this.InitProjectionInfo();
    }

    @Override
    Tuple getNextRow() {
        if (initJoin == false) {
            innerTuple = this.innerNode.getNextRow();
            initJoin = true;
        }
        Tuple outerTuple, resultTuple = null;
        outerTuple = this.outerNode.getNextRow();

        if (outerTuple != null) {
            return new Tuple(innerTuple, outerTuple);
        } else {
            this.outerNode.resetIterator();
            innerTuple = this.innerNode.getNextRow();
            outerTuple = this.outerNode.getNextRow();
            if (innerTuple == null || outerTuple == null)
                return null;
            else
                return new Tuple(innerTuple, outerTuple);
        }
    }

    @Override
    void resetIterator() {
        this.innerTuple = null;
        this.initJoin = false;
        this.innerNode.resetIterator();
        this.outerNode.resetIterator();
    }

    @Override
    void InitProjectionInfo() {
        this.ProjectionInfo = new ArrayList<>(this.innerNode.ProjectionInfo);
        this.ProjectionInfo.addAll(this.outerNode.ProjectionInfo);
    }
}
