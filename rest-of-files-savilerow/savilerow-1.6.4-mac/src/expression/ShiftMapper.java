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
import java.math.*;
import savilerow.model.SymbolTable;

public class ShiftMapper extends ASTNode
{
    public static final long serialVersionUID = 1L;
	public ShiftMapper(ASTNode a, ASTNode shift) {
	    super(a, shift);
	}
	
	public ASTNode copy()
	{
	    return new ShiftMapper(getChild(0), getChild(1));
	}
	
	public boolean toFlatten(boolean propagate) { return false;}  // Never flatten this. 
	
    public ASTNode simplify() {
        assert getChild(0).getCategory()==ASTNode.Decision || getChild(0).getCategory()==ASTNode.Undeclared;
        assert getChild(1).getCategory()<=ASTNode.Quantifier || getChild(1).getCategory()==ASTNode.Undeclared;
        
        
        if(getChild(1).equals(new NumberConstant(0))) {
            return getChild(0);
        }
        
        if(getChild(0) instanceof ShiftMapper) {
            // Put the two shiftmappers together.
            ASTNode newshift=BinOp.makeBinOp("+", getChild(1), getChild(0).getChild(1));
            //newshift=newshift.simplify();
            return new ShiftMapper(getChild(0).getChild(0), newshift);
        }
        return this;
    }
    
    public Intpair getBounds() {
        Intpair a=getChild(0).getBounds();
        Intpair shift=getChild(1).getBounds();
        // Saturates at Long.MAX_VALUE and Long.MIN_VALUE
        a.lower=Intpair.BigIntegerToLong(BigInteger.valueOf(a.lower).add(BigInteger.valueOf(shift.lower)));
        a.upper=Intpair.BigIntegerToLong(BigInteger.valueOf(a.upper).add(BigInteger.valueOf(shift.upper)));
        return a;
    }
    public PairASTNode getBoundsAST() {
        PairASTNode a=getChild(0).getBoundsAST();
        a.e1=BinOp.makeBinOp("+", a.e1, getChild(1));
        a.e2=BinOp.makeBinOp("+", a.e2, getChild(1));
        return a;
    }
    
	public String toString() {
	    return "shift("+getChild(0)+", "+getChild(1)+")";
	}
	public void toDominionInner(StringBuffer b, boolean bool_context) {
	    b.append("shift(");
	    getChild(0).toDominion(b, false);
	    b.append(",");
	    getChild(1).toDominion(b, false);
	    b.append(")");
	}
}
