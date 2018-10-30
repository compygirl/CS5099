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
import savilerow.model.*;

// Takes list of target variables, number of occurrences, and value. Each argument is a list.
// Constrains the number of occurrences of the values in the list to be at least occs.

// c0 is vector
// c1 is counts
// c2 is values
public class AtLeast extends ASTNode {
    public static final long serialVersionUID = 1L;
    public AtLeast(ASTNode v, ASTNode occ, ASTNode val) {
        super(v, occ, val);
    }

    public ASTNode copy() {
        return new AtLeast(getChild(0), getChild(1), getChild(2));
    }
    public boolean typecheck(SymbolTable st) {
        for (int i =0; i < 3; i++) {
            if (!getChild(i).typecheck(st)) {
                return false;
            }
            if (getChild(i).getDimension() != 1) {
                CmdFlags.println("ERROR: Expected one-dimensional matrix for each argument of atleast constraint: " + this);
                return false;
            }
        }
        if (getChild(1).getCategory() > ASTNode.Quantifier || getChild(2).getCategory() > ASTNode.Quantifier) {
            CmdFlags.println("ERROR: Atleast functions do not allow decision variables in the second or third arguments: " + this);
            return false;
        }
        if( (getChild(1) instanceof CompoundMatrix || getChild(1) instanceof EmptyMatrix) &&
            (getChild(2) instanceof CompoundMatrix || getChild(2) instanceof EmptyMatrix) )
        {
            if(getChild(1).numChildren() != getChild(2).numChildren()) {
                CmdFlags.println("ERROR: Atleast function expects second and third arguments to be the same length: " + this);
                return false;
            }
        }
        return true;
    }
    public ASTNode simplify() {
        if (getChild(1) instanceof EmptyMatrix) {
            assert getChild(2) instanceof EmptyMatrix;
            // There are no value occurrence restrictions.
            return new BooleanConstant(true);
        }

        // Filter out occurrences that are 0 or less.
        if(getChild(0) instanceof CompoundMatrix && getChild(1) instanceof CompoundMatrix && getChild(2) instanceof CompoundMatrix) {
            for (int i =1; i < getChild(1).numChildren(); i++) {
                if (getChild(1).getChild(i).isConstant()) {
                    if(getChild(1).getChild(i).getValue() <= 0) {
                        ArrayList<ASTNode> c1 = getChild(1).getChildren();
                        c1.remove(i);
                        c1.remove(0);                    // remove the index.
    
                        ArrayList<ASTNode> c2 = getChild(2).getChildren();
                        c2.remove(i);
                        c2.remove(0);                    // remove the index.
    
                        return new AtLeast(getChild(0), CompoundMatrix.makeCompoundMatrix(c1), CompoundMatrix.makeCompoundMatrix(c2));
                    }
                    else if(getChild(1).getChild(i).getValue() > getChild(0).numChildren() - 1) {
                        // At least n+1 occs of a value is impossible. 
                        return new BooleanConstant(false);
                    }
                }
            }
        }

        // Now occurrence list is non-empty and non-zero.
        
        // Case where target list is empty.
        if (getChild(0) instanceof EmptyMatrix && (getChild(1) instanceof CompoundMatrix || getChild(1) instanceof EmptyMatrix)) {
            // Replace AtLeast with constraints to say each card expression is <=0
            ArrayList<ASTNode> conjunction=new ArrayList<ASTNode>();
            for (int i =1; i < getChild(1).numChildren(); i++) {
                conjunction.add(new LessEqual(getChild(1).getChild(i), new NumberConstant(0)));
            }
            return new And(conjunction);
        }
        
        if (getChild(0) instanceof CompoundMatrix && getChild(1).getCategory() == ASTNode.Constant && getChild(2).getCategory() == ASTNode.Constant) {
            ArrayList<ASTNode> a = getChild(0).getChildren();
            a.remove(0);            // throw away index.

            ArrayList<ASTNode> vals = getChild(2).getChildren();
            vals.remove(0);
            ArrayList<ASTNode> occs = getChild(1).getChildren();
            occs.remove(0);

            for (int i =0; i < a.size(); i++) {
                if (a.get(i).isConstant()) {
                    for(int j=0; j<vals.size(); j++) {
                        if(vals.get(j).equals(a.get(i))) {
                            occs.set(j, BinOp.makeBinOp("-", occs.get(j), new NumberConstant(1L)));
                        }
                    }
                    
                    a.remove(i);

                    return new AtLeast(CompoundMatrix.makeCompoundMatrix(a), CompoundMatrix.makeCompoundMatrix(occs), getChild(2));
                }
            }
        }
        return this;
    }
    public ASTNode normalise() {
        if (!(getChild(0) instanceof CompoundMatrix)) {
            return this;
        }

        // sort by hashcode
        ArrayList<ASTNode> ch = getChild(0).getChildren();
        ch.remove(0);
        boolean changed = sortByHashcode(ch);
        // ch can't be empty.
        if (changed) {
            return new AtLeast(new CompoundMatrix(ch), getChild(1), getChild(2));
        } else {
            return this;
        }
    }

    public boolean isRelation() { return true; }

    public void toMinion(StringBuffer b, boolean bool_context) {
        assert getChild(2).getCategory() == ASTNode.Constant;
        assert bool_context;
        assert getChild(1) instanceof CompoundMatrix && getChild(1).numChildren() == 2;
        assert getChild(2) instanceof CompoundMatrix && getChild(2).numChildren() == 2;
        b.append("occurrencegeq(");
        getChild(0).toMinion(b, false);
        b.append(", ");
        getChild(2).getChild(1).toMinion(b, false);
        b.append(", ");
        getChild(1).getChild(1).toMinion(b, false);
        b.append(")");
    }
    public void toDominionInner(StringBuffer b, boolean bool_context) {
        assert getChild(2).getCategory() == ASTNode.Constant;
        b.append(CmdFlags.getCtName() + " ");
        b.append("occurrencegeq(flatten(");
        getChild(0).toDominion(b, false);
        b.append("), ");
        getChild(2).getChild(1).toDominion(b, false);
        b.append(", ");
        getChild(1).getChild(1).toDominion(b, false);
        b.append(")");
    }
    public void toFlatzinc(StringBuffer b, boolean bool_context) {
        b.append("constraint at_least_int(");
        getChild(1).getChild(1).toFlatzinc(b, false);
        b.append(", ");
        getChild(0).toFlatzinc(b, false);
        b.append(", ");
        getChild(2).getChild(1).toFlatzinc(b, false);
        b.append(");");
    }
    public String toString() {
        return "atleast(" + getChild(0) + "," + getChild(1) + "," + getChild(2) + ")";
    }
    public void toMinizinc(StringBuffer b, boolean bool_context) {
        b.append("at_least(");
        getChild(1).getChild(1).toMinizinc(b, false);
        b.append(",");
        getChild(0).toMinizinc(b, false);
        b.append(",");
        getChild(2).getChild(1).toMinizinc(b, false);
        b.append(")");
    }

    public void toJSON(StringBuffer bf) {
        GlobalCard.toAlternateJSON(this, bf);
    }

    public boolean isChildSymmetric(int childIndex) {
        return childIndex == 0;
    }
}
