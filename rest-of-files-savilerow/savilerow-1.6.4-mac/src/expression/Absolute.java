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
import savilerow.treetransformer.*;
import savilerow.model.Sat;


public class Absolute extends Unary {
    public static final long serialVersionUID = 1L;
    public Absolute(ASTNode a) {
        super(a);
    }

    public ASTNode copy() {
        return new Absolute(getChild(0));
    }

    public ASTNode simplify() {
        if (getChild(0).isConstant()) {
            long val = getChild(0).getValue();
            if (val < 0) {
                return new NumberConstant(-val);
            } else {
                return new NumberConstant(val);
            }
        }
        
        Intpair p = getChild(0).getBounds();
        if(p.lower >= 0) {
            return getChild(0);
        }
        if(p.upper <= 0) {
            return new UnaryMinus(getChild(0));
        }
        
        return this;
    }
    public boolean typecheck(SymbolTable st) {
        if (!getChild(0).typecheck(st)) {
            return false;
        }
        if (getChild(0).getDimension() > 0) {
            CmdFlags.println("ERROR: Cannot apply absolute value operation to a matrix: " + this);
            return false;
        }
        return true;
    }

    public Intpair getBounds() {
        Intpair a = getChild(0).getBounds();
        if(a.lower==Long.MIN_VALUE) {
            a.lower++;   //  Make sure a.lower can be safely negated -- Long.MIN_VALUE can't be.
        }
        if (a.upper < 0) {            // reflect by 0
            long temp = a.upper;
            a.upper = -a.lower;
            a.lower = -temp;
        }
        else if (a.lower < 0) {            // interval includes 0.
            if (a.upper > (-a.lower)) {
                a.lower = 0;
            } else {
                a.upper = -a.lower;
                a.lower = 0;
            }
        }
        return lookupBounds(a);    //  Look up in FilteredDomainStore
    }

    public PairASTNode getBoundsAST() {
        PairASTNode a = getChild(0).getBoundsAST();
        PairASTNode b = new PairASTNode(null, null);
        b.e1 = new Max(a.e1, new NumberConstant(0));
        b.e2 = new Max(a.e2, new UnaryMinus(a.e1));
        return b;
    }

    public boolean toFlatten(boolean propagate) { return true; }
    public boolean isNumerical() {
        return true;
    }

    public void toMinionWithAuxVar(StringBuffer b, ASTNode aux) {
        b.append("abs(");
        aux.toMinion(b, false);
        b.append(", ");
        getChild(0).toMinion(b, false);
        b.append(")");
    }

    public void toDominionWithAuxVar(StringBuffer b, ASTNode aux) {
        b.append(CmdFlags.getCtName() + " ");
        b.append("abs(");
        aux.toDominion(b, false);
        b.append(", ");
        getChild(0).toDominion(b, false);
        b.append(")");
    }
    public void toDominionParam(StringBuffer b) {
        b.append("Abs(");
        getChild(0).toDominionParam(b);
        b.append(")");
    }
    
    public String toString() {
        return "|" + getChild(0) + "|";
    }
    public void toFlatzincWithAuxVar(StringBuffer b, ASTNode aux) {
        b.append("constraint int_abs(");
        getChild(0).toFlatzinc(b, false);
        b.append(", ");
        aux.toFlatzinc(b, false);
        b.append(");");
    }
    
    public void toMinizinc(StringBuffer b, boolean bool_context) {
        assert(!bool_context);
        b.append("abs(");
        getChild(0).toMinizinc(b, bool_context);
        b.append(")");
    }
    
    public void toSATWithAuxVar(Sat satModel, ASTNode equals) throws IOException {
        satModel.supportEncodingBinary(this,getChild(0),equals);
    }
    
    public boolean test(long valueLeft, long valueRight) {
        return (valueLeft<0 ? -valueLeft : valueLeft)==valueRight;
    }
    
    public boolean canChildBeConvertedToDifference(int childIndex) {
        return true;
    }
    
}
