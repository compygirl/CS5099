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
import java.io.*;
import savilerow.model.SymbolTable;
import savilerow.model.Sat;
// 

public class Mod extends BinOp
{
    public static final long serialVersionUID = 1L;
	public Mod(ASTNode l, ASTNode r)
	{
		super(l, r);
	}
	
	public ASTNode copy()
	{
	    return new Mod(getChild(0), getChild(1));
	}
	
	public ASTNode simplify()	{
	    
	    if(getChild(0).isConstant() && getChild(1).isConstant()) {
	        long a=getChild(0).getValue();
	        long b=getChild(1).getValue();
	        if(b==0) {
	            return this;  // Leave it, should be dealt with elsewhere.
	        }
	        
	        return new NumberConstant(mod(a,b));
	    }
	    return this;
	}
	
	public long mod(long a, long b) {
	    return Intpair.BigIntegerToLong(BigInteger.valueOf(a).subtract(BigInteger.valueOf(Divide.div(a, b)).multiply(BigInteger.valueOf(b))));
	    //return a- (long)(Math.floor(((double)a)/((double)b))*b);
	}
	
	public Intpair getBounds() {
	    Intpair num=getChild(0).getBounds();
	    Intpair denom=getChild(1).getBounds();
	    
	    // Simple case is where the denominator is positive.
	    if(denom.lower>=0) {
            // Take one off the top
            denom.upper=denom.upper-1;
            denom.lower=0L;
            return lookupBounds(denom);    //  Look up in FilteredDomainStore
        }
        if(denom.upper<=0) {
            denom.lower=denom.lower+1;
            denom.upper=0L;
            return lookupBounds(denom);    //  Look up in FilteredDomainStore
        }
        
        // Base could be positive or negative.
        denom.upper=denom.upper-1;
        denom.lower=denom.lower+1;
        
        return lookupBounds(denom);
	}
	public PairASTNode getBoundsAST() {
	    // Not tight bounds. -- should also look at the first child.
	    PairASTNode a=getChild(1).getBoundsAST();
	    
	    // Narrow range by 1 at both ends.
	    a.e1=BinOp.makeBinOp("+", a.e1, new NumberConstant(1));
	    a.e2=BinOp.makeBinOp("-", a.e2, new NumberConstant(1));
	    
	    // Extend the interval to include 0. 
	    a.e1=new Min(new NumberConstant(0), a.e1);
	    a.e2=new Max(new NumberConstant(0), a.e2);
	    return a;
	}
	
	public boolean toFlatten(boolean propagate) { return true;}
	public boolean isNumerical() {
        return true;
    }
    
	public void toMinionWithAuxVar(StringBuffer b, ASTNode aux) {
	    b.append("modulo(");
	    getChild(0).toMinion(b, false);
	    b.append(",");
	    getChild(1).toMinion(b, false);
	    b.append(",");
	    aux.toMinion(b, false);
	    b.append(")");
	}
	public void toDominionWithAuxVar(StringBuffer b, ASTNode aux) {
	    b.append(CmdFlags.getCtName()+" ");
	    b.append("modulo(");
	    getChild(0).toDominion(b, false);
	    b.append(",");
	    getChild(1).toDominion(b, false);
	    b.append(",");
	    aux.toDominion(b, false);
	    b.append(")");
	}
	public void toDominionParam(StringBuffer b) {
	    b.append("Mod(");
	    getChild(0).toDominionParam(b);
	    b.append(",");
	    getChild(1).toDominionParam(b);
	    b.append(")");
	}
	public void toDominionInner(StringBuffer b, boolean bool_context) {
	    assert getCategory()<=ASTNode.Quantifier;
	    toDominionParam(b);
	}
	public String toString() {
	    return "("+getChild(0)+"%"+getChild(1)+")";
	}
	public void toFlatzincWithAuxVar(StringBuffer b, ASTNode aux) {
	    b.append("constraint int_mod(");
	    getChild(0).toFlatzinc(b, false);
	    b.append(",");
	    getChild(1).toFlatzinc(b, false);
	    b.append(",");
	    aux.toFlatzinc(b, false);
	    b.append(");");
	}
	public void toMinizinc(StringBuffer b, boolean bool_context) {
	    b.append("(");
	    getChild(0).toMinizinc(b, false);
	    b.append(" mod ");
	    getChild(1).toMinizinc(b, false);
	    b.append(")");
	}
	
	////////////////////////////////////////////////////////////////////////////
	//
	//   SAT encoding
	
    public void toSATWithAuxVar(Sat satModel, ASTNode auxVar) throws IOException {
        satModel.ternaryEncoding(this, auxVar);
    }
    
    public boolean test(long val1, long val2, long aux) {
        return mod(val1, val2)==aux;
    }
    public long func(long val1, long val2) {
        return mod(val1, val2);
    }
}