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

import savilerow.*;
import java.util.*;
import java.io.*;
import savilerow.model.SymbolTable;
import savilerow.model.Sat;

// One child, a matrix type

public class AllDifferent extends ASTNode {
    public static final long serialVersionUID = 1L;
    public AllDifferent(ASTNode r) {
        super(r);
    }

    // Ctor for a binary disequality.
    public AllDifferent(ASTNode a, ASTNode b) {
        ArrayList<ASTNode> n = new ArrayList<ASTNode>();
        n.add(a); n.add(b);
        this.setChildren(new CompoundMatrix(n));
    }

    public ASTNode copy() {
        assert numChildren() == 1;
        return new AllDifferent(getChild(0));
    }

    public boolean isRelation() { return true; }

    public boolean typecheck(SymbolTable st) {
        if (!getChild(0).typecheck(st)) {
            return false;
        }
        if (getChild(0).getDimension() != 1) {
            CmdFlags.println("ERROR: Expected one-dimensional matrix in allDiff constraint: " + this);
            return false;
        }
        return true;
    }
    public ASTNode simplify() {
        if (getChild(0) instanceof EmptyMatrix) {
            return new BooleanConstant(true);
        }
        if (getChild(0) instanceof CompoundMatrix) {
            ASTNode ch = getChild(0);
            for (int i =1; i < ch.numChildren(); i++) {
                for (int j = i + 1; j < ch.numChildren(); j++) {
                    if (ch.getChild(i).equals(ch.getChild(j))) {
                        return new BooleanConstant(false);                        // symbolic equality of two items.
                    }

                    if (ch.getChild(i).isConstant() && ch.getChild(j).isConstant() && ch.getChild(i).getValue() == ch.getChild(j).getValue()) {
                        return new BooleanConstant(false);                        // numerical equality of two items, both constants. e.g. false=0.
                    }
                }
            }

            if (ch.numChildren() < 3) {
                return new BooleanConstant(true);
            }            // One or zero elements are always pairwise different.
            
            // Filter out any constants that are not within the bounds of any other element in the alldiff.
            ArrayList<ASTNode> items = ch.getChildren();
            items.remove(0);
            boolean changed = false;

            for (int i =0; i < items.size(); i++) {
                if (items.get(i).isConstant()) {
                    boolean intersects = false;                    // does item i intersect with any other.
                    long val = items.get(i).getValue();

                    for (int j =0; j < items.size(); j++) {
                        if (i != j) {
                            Intpair p = items.get(j).getBounds();

                            if (val >= p.lower && val <= p.upper) {

                                if (items.get(j) instanceof Identifier) {
                                    // Get the full domain.
                                    ASTNode dom = ((Identifier) items.get(j)).getDomain();

                                    if (dom.getCategory() == ASTNode.Constant) {
                                        if (dom.containsValue(val)) {
                                            intersects = true;
                                            break;
                                        } else {
                                            continue;                                            // Does not intersect.
                                        }
                                    }
                                    // Only bounds are available -- fall through.
                                }

                                // Only the bounds are available.
                                intersects = true;
                                break;
                            }
                        }
                    }

                    if (!intersects) {
                        // remove i from the alldiff.
                        items.remove(i);
                        i--;
                        changed = true;
                    }
                }
            }

            if (changed) {
                return new AllDifferent(CompoundMatrix.makeCompoundMatrix(items));
            }
        }
        return this;
    }

    @Override
    public boolean isNegatable() {
        return getChild(0) instanceof CompoundMatrix && getChild(0).numChildren() == 3;
    }
    @Override
    public ASTNode negation() {
        assert getChild(0) instanceof CompoundMatrix && getChild(0).numChildren() == 3;
        return new Equals(getChild(0).getChild(1), getChild(0).getChild(2));
    }

    public ASTNode normalise() {
        // sort by hashcode
        // Insertion sort -- behaves well with almost-sorted lists
        if (!(getChild(0) instanceof CompoundMatrix)) {
            return this;
        }

        ArrayList<ASTNode> ch = getChild(0).getChildren();
        ch.remove(0);        // throw away index.

        boolean changed = sortByHashcode(ch);
        if (changed) {
            return new AllDifferent(new CompoundMatrix(ch));
        } else {
            return this;
        }

    }

    public void toMinion(StringBuffer b, boolean bool_context) {
        assert bool_context;
        assert numChildren() == 1;
        ASTNode ch = getChild(0);
        if (ch instanceof CompoundMatrix && ch.numChildren() == 3) {
            b.append("diseq(");
            ch.getChild(1).toMinion(b, false);
            b.append(",");
            ch.getChild(2).toMinion(b, false);
            b.append(")");
        } else {
            String ctname = "gacalldiff";
            if (ch instanceof CompoundMatrix) {
                for (int i =1; i < ch.numChildren(); i++) {
                    if (CmdFlags.getUseBoundVars() && ch.getChild(i).exceedsBoundThreshold()) {
                        ctname = "alldiff";
                        break;
                    }
                }
            }

            b.append(ctname);
            b.append("(");
            getChild(0).toMinion(b, false);
            b.append(")");
        }
    }
    public void toDominionParam(StringBuffer b) {
        assert getChild(0) instanceof CompoundMatrix;
        assert getChild(0).numChildren() == 3;
        b.append("(");
        getChild(0).getChild(1).toDominionParam(b);
        b.append("!=");
        getChild(0).getChild(2).toDominionParam(b);
        b.append(")");
    }
    public void toDominionInner(StringBuffer b, boolean bool_context) {
        if (getCategory() <= ASTNode.Quantifier) {
            toDominionParam(b);            // Wot if it's inside an and or something?? it won't accept a non-constraint argument.
            return;
        }
        assert numChildren() == 1;
        ASTNode ch = getChild(0);
        b.append(CmdFlags.getCtName() + " ");
        if (ch instanceof CompoundMatrix && ch.numChildren() == 3) {
            b.append("noteq(");
            ch.getChild(1).toDominion(b, false);
            b.append(",");
            ch.getChild(2).toDominion(b, false);
            b.append(")");
        } else {
            b.append("alldiff(flatten(");
            getChild(0).toDominion(b, false);
            b.append("))");
        }
    }
    public String toString() {
        if (getChild(0) instanceof CompoundMatrix && getChild(0).numChildren() == 3) {
            return "(" + getChild(0).getChild(1) + " != " + getChild(0).getChild(2) + ")";
        }
        return "allDiff(" + getChild(0) + ")";
    }
    public void toFlatzinc(StringBuffer b, boolean bool_context) {
        assert numChildren() == 1;
        ASTNode ch = getChild(0);
        if (ch instanceof CompoundMatrix && ch.numChildren() == 3) {
            b.append("constraint int_ne(");            /// This case will work with reification
            ch.getChild(1).toFlatzinc(b, false);
            b.append(",");
            ch.getChild(2).toFlatzinc(b, false);
            b.append(");");
        } else {
            b.append("constraint all_different_int(");            /// This case will not work with reification -- decompose alldiff before getting here.
            getChild(0).toFlatzinc(b, false);
            b.append(")::domain;");
        }
    }
    public void toMinizinc(StringBuffer b, boolean bool_context) {
        ASTNode ch = getChild(0);
        if (ch instanceof CompoundMatrix && ch.numChildren() == 3) {
            b.append("(");
            ch.getChild(1).toMinizinc(b, false);
            b.append("!=");
            ch.getChild(2).toMinizinc(b, false);
            b.append(")");
        } else {
            b.append("all_different(");
            getChild(0).toMinizinc(b, false);
            b.append(")");
        }
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    //   SAT encoding of binary not-equal only. Longer AllDifferents should be
    //   decomposed before output. 
    
    public Long toSATLiteral(Sat satModel) {
        assert getChild(0).numChildren()==3;
        ASTNode ch0=getChild(0).getChild(1);
        ASTNode ch1=getChild(0).getChild(2);
        
	    if(ch0.isConstant()) {
	        return -ch1.directEncode(satModel, ch0.getValue());
        }
        if(ch1.isConstant()) {
            return -ch0.directEncode(satModel, ch1.getValue());
        }
        return null;
	}
    
    public void toSAT(Sat satModel) throws IOException
    {
        assert getChild(0).numChildren()==3;
        //  Direct encoding of pairwise not-equal constraints.
        ASTNode ch = getChild(0);
        for (int i=1; i < ch.numChildren(); i++) {
            for (int j=i+1; j<ch.numChildren(); j++) {
                //satModel.supportEncodingBinary(this, ch.getChild(i), ch.getChild(j));
                satModel.directEncoding(this, ch.getChild(i), ch.getChild(j));
            }
        }
    }
    
    public void toSATWithAuxVar(Sat satModel, long reifyVar) throws IOException {
        assert getChild(0).numChildren()==3;
        
        new Equals(getChild(0).getChild(1), getChild(0).getChild(2)).toSATWithAuxVar(satModel, -reifyVar);
    }
    
    //   Test function represents binary not-equals
    public boolean test(long val1, long val2) {
        return val1!=val2;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //  JSON output for symmetry detection
    
    public void toJSON(StringBuffer bf) {
        toJSONHeader(bf, true);
        // children
        bf.append("\"Children\": [");
        if(getChild(0) instanceof CompoundMatrix && getChild(0).numChildren()==3) {
            //   Special case for binary != constraint.
            getChild(0).getChild(1).toJSON(bf);
            bf.append(", ");
            getChild(0).getChild(2).toJSON(bf);
        }
        else {
            // Same as toJSON method in ASTNode.
            for (int i = 0; i < numChildren(); i++) {
                bf.append("\n");
                getChild(i).toJSON(bf);
                // not last child
                if (i < numChildren() - 1) {
                    bf.append(",");
                }
            }
        }
        bf.append("]\n}");
    }
    
    public boolean childrenAreSymmetric() {
        return (getChild(0) instanceof CompoundMatrix && getChild(0).numChildren()==3);
    }
    
    public boolean isChildSymmetric(int childIndex) {
        // If not a binary != ct, then the matrix inside should be regarded as symmetric.
        return !(getChild(0) instanceof CompoundMatrix && getChild(0).numChildren()==3);
    }

    public boolean canChildBeConvertedToDifference(int childIndex) {
        return isMyOnlyOtherSiblingEqualZero(childIndex);
    }

}
