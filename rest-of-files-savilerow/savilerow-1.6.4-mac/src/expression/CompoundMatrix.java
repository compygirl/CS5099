package savilerow.expression;
/*

    Savile Row http://savilerow.cs.st-andrews.ac.uk/
    Copyright (C) 2014, 2015 Peter Nightingale
    
    This file is part of Savile Row.
    
    Savile Row is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    
    Savile Row is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
    
    You should have received a copy of the GNU General Public License
    along with Savile Row.  If not, see <http://www.gnu.org/licenses/>.

*/

import java.util.*;
import savilerow.model.*;

public class CompoundMatrix extends ASTNode {
    public static final long serialVersionUID = 1L;
    // One-dimensional matrix made up of expressions.
    // May be nested. Child 0 is an index.
    public CompoundMatrix(ASTNode idx, ArrayList<ASTNode> m) {
        ArrayList<ASTNode> a = new ArrayList<ASTNode>();
        a.add(idx); a.addAll(m);
        setChildren(a);
        assert m.size() > 0;
    }

    // CTOR with default index range of 1..n
    public CompoundMatrix(ArrayList<ASTNode> m) {
        ASTNode idx = new IntegerDomain(new Range(new NumberConstant(1), new NumberConstant(m.size())));
        ArrayList<ASTNode> a = new ArrayList<ASTNode>();
        a.add(idx); a.addAll(m);
        setChildren(a);
        assert m.size() > 0;
    }

    // factory method for flat compound matrices. If it has to make an EmptyMatrix,
    // it assumes 1-d
    public static ASTNode makeCompoundMatrix(ASTNode idx, ArrayList<ASTNode> m, boolean isBooleanMatrix) {
        if (m.size() > 0) {
            return new CompoundMatrix(idx, m);
        } else {
            ArrayList<ASTNode> idxdoms = new ArrayList<ASTNode>();
            idxdoms.add(idx);
            ASTNode basedom = isBooleanMatrix ? new BooleanDomain(new EmptyRange()) : new IntegerDomain(new EmptyRange());
            return new EmptyMatrix(new MatrixDomain(basedom, idxdoms));
        }
    }

    public static ASTNode makeCompoundMatrix(ArrayList<ASTNode> m) {
        // Assume type integer, assume indexed from 1.
        if (m.size() == 0) {
            return makeCompoundMatrix(new IntegerDomain(new EmptyRange()), m, false);
        } else {
            return makeCompoundMatrix(new IntegerDomain(new Range(new NumberConstant(1), new NumberConstant(m.size()))), m, false);
        }
    }
    
    // Just one entry in matrix.
    public static ASTNode makeCompoundMatrix(ASTNode a) {
        // Assume type integer, assume indexed from 1.
        ArrayList<ASTNode> m=new ArrayList<ASTNode>();
        m.add(a);
        return new CompoundMatrix(m);
    }
    
    // Two entries in matrix.
    public static ASTNode makeCompoundMatrix(ASTNode a, ASTNode b) {
        // Assume type integer, assume indexed from 1.
        ArrayList<ASTNode> m=new ArrayList<ASTNode>();
        m.add(a); m.add(b);
        return new CompoundMatrix(m);
    }
    
    public boolean isRelation() {
        // For this to be relational, each element must be relational. 
        boolean all=true;
        for(int i=1; i<numChildren(); i++) {
            if(!getChild(i).isRelation()) { 
                all=false;
                break;
            }
        }
        return all;
    }
    public boolean isNumerical() {
        // For this to be numerical, one element must be numerical. (Others may be bools that are cast to int) 
        boolean one=false;
        for(int i=1; i<numChildren(); i++) {
            if(getChild(i).isNumerical()) { 
                one=true;
                break;
            }
        }
        return one;
    }
    public boolean isSet() {
        // All elements must be sets
        for(int i=1; i<numChildren(); i++) {
            if(!getChild(i).isSet()) { 
                return false;
            }
        }
        return true;
    }
    
    public ASTNode copy() {
        ArrayList<ASTNode> entries = getChildren();
        entries.remove(0);
        return new CompoundMatrix(getChild(0), entries);
    }
    public boolean toFlatten(boolean propagate) { return false; }
    
    //  Bounds on a matrix are defined as the bounds on all expressions inside the matrix. 
    public Intpair getBounds() {
        Intpair bnds=getChild(1).getBounds();
        for (int i=2; i < numChildren(); i++) {
            Intpair a=getChild(i).getBounds();
            if(a.lower<bnds.lower) bnds.lower=a.lower;
            if(a.upper>bnds.upper) bnds.upper=a.upper;
        }
        return bnds;
    }
    // Also not an expression type with numerical bounds!
    // Used for simplify e.g. a safeelement contained in an equals.
    public PairASTNode getBoundsAST() {
        ArrayList<ASTNode> mins = new ArrayList<ASTNode>();
        ArrayList<ASTNode> maxs = new ArrayList<ASTNode>();
        for (int i=1; i < numChildren(); i++) {
            PairASTNode a = getChild(i).getBoundsAST();
            mins.add(a.e1);
            maxs.add(a.e2);
        }
        return new PairASTNode(new Min(mins), new Max(maxs));
    }
    
    @Override
    public boolean typecheck(SymbolTable st) {
        for(int i=0; i<numChildren(); i++) {
            if(!getChild(i).typecheck(st)) return false; 
        }
        
        // Must have uniform dimension.
        int dim=getChild(1).getDimension();
        for(int i=2; i<numChildren(); i++) {
            if(getChild(i).getDimension()!=dim) {
                System.out.println("ERROR: In matrix literal: "+this); 
                System.out.println("ERROR: Elements in matrix literal have different numbers of dimensions.");
                return false;
            }
        }
        
        if(!getChild(0).isFiniteSet()) {
            System.out.println("ERROR: In matrix literal: "+this); 
            System.out.println("ERROR: Index set is not a finite set.");
            return false;
        }
        
        // Is the index set the right size?
        
        
        //  Not yet checking distinction between set and int/bool.
        
	    return true;
	}
    
    // Assumes the dimension is the same everywhere.
    public int getDimension() {
        if (numChildren() == 1) {
            assert false : "CompoundMatrix type must have non-zero size.";
            return 1;
        }
        else {
            return 1 + getChild(1).getDimension();
        }
    }
    
    public ArrayList<ASTNode> getIndexDomains() {
        ArrayList<ASTNode> tmp;
        assert numChildren() >= 2;
        tmp = getChild(1).getIndexDomains();
        if (tmp == null) {
            // Not a matrix inside.
            tmp = new ArrayList<ASTNode>();
        }
        
        tmp.add(0, getChild(0));
        return tmp;
    }
    
    // ALL output methods except E' drop the index.
    public void toMinion(StringBuffer b, boolean bool_context) {
        assert !bool_context;
        b.append("[");
        for (int i =1; i < numChildren(); i++) {
            getChild(i).toMinion(b, false);
            if (i < numChildren() - 1) {
                b.append(", ");
            }
        }
        b.append("]");
    }
    public void toDominionInner(StringBuffer b, boolean bool_context) {
        b.append("[");
        for (int i =1; i < numChildren(); i++) {
            getChild(i).toDominion(b, false);
            if (i < numChildren() - 1) {
                b.append(", ");
            }
        }
        b.append("]");
    }
    public void toDominionParam(StringBuffer b) {
        b.append("[");
        for (int i =1; i < numChildren(); i++) {
            getChild(i).toDominionParam(b);
            if (i < numChildren() - 1) {
                b.append(", ");
            }
        }
        b.append("]");
    }

    public String toString() {
        StringBuffer st = new StringBuffer();
        st.append("[");
        for (int i =1; i < numChildren(); i++) {
            st.append(getChild(i).toString());

            if (i < numChildren() - 1) {
                st.append(", ");
                if (getChild(i) instanceof CompoundMatrix || getChild(i) instanceof EmptyMatrix) {
                    st.append("\n");
                }
            }
        }

        st.append(";");

        st.append(getChild(0).toString());
        st.append("]");
        return st.toString();
    }

    public void toFlatzinc(StringBuffer b, boolean bool_context) {
        b.append("[");
        for (int i =1; i < numChildren(); i++) {
            getChild(i).toFlatzinc(b, bool_context);
            if (i < numChildren() - 1) {
                b.append(", ");
            }
        }
        b.append("]");
    }

    public void toMinizinc(StringBuffer b, boolean bool_context) {
        b.append("[");
        for (int i =1; i < numChildren(); i++) {
            getChild(i).toMinizinc(b, bool_context);
            if (i < numChildren() - 1) {
                b.append(", ");
            }
        }
        b.append("]");
    }

    public void toJSON(StringBuffer bf) {
        toJSONHeader(bf, true);
        // children
        bf.append("\"Domain\":");
        getChild(0).toJSON(bf);
        bf.append(",\n");
        bf.append("\"Children\": [");
        int numberChildren = numChildren();
        // skip first child as this is domain
        for (int i = 1; i < numChildren(); i++) {
            bf.append("\n");
            getChild(i).toJSON(bf);
            // not last child
            if (i < numberChildren - 1) {
                bf.append(",");
            }
        }
        bf.append("]\n}");
    }

    public boolean childrenAreSymmetric() {
        return getParent().isChildSymmetric(getChildNo());
    }
}
