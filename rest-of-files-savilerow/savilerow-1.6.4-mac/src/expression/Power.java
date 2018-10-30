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
import java.lang.Math;
import savilerow.model.Sat;

public class Power extends BinOp
{
    public static final long serialVersionUID = 1L;
	public Power(ASTNode l, ASTNode r)
	{
		super(l, r);
	}
	
	public ASTNode copy() {
	    return new Power(getChild(0), getChild(1));
	}
	
	public boolean toFlatten(boolean propagate) { return true; }
	public boolean isNumerical() {
        return true;
    }
    
	public ASTNode simplify()	{
	    
	    if(getChild(0).isConstant() && getChild(1).isConstant()) {
	        long a=getChild(0).getValue();
	        long b=getChild(1).getValue();
	        if((a==0 && b==0) || b<0) {
	            return this;  // undefined, delay.
	        }
	        return new NumberConstant(pow(a,b));
	    }
	    if(getChild(1).isConstant() && getChild(1).getValue()==1) {
	        return getChild(0);
	    }
	    
	    return this;
	}
	
	// Use 0 as a default value, as in SafePower.
	public static long pow(long a, long b) {
	    if((a==0 && b==0) || b<0L) {
	        return 0L;  // Undefined, return default value. 
	    }
	    if(b==0L) {
	        // non-zero a.
	        return 1L;
	    }
	    
	    if(b>Integer.MAX_VALUE) {
	        // Can't use b as an argument to BigInteger.pow
	        if(a==0L || a==1L) {
	            return a;
	        }
	        else if(a==-1) {
	            return (b%2 == 0)?1:-1;
	        }
	        else if(a<-1) {
	            if(b%2 == 0) {
	                return Long.MAX_VALUE;   // Negative to an even power.
	            }
	            else {
	                return Long.MIN_VALUE;   // negative to an odd power.
	            }
	        }
	        else {   // a>1
	            return Long.MAX_VALUE;  // +infty
	        }
	    }
	    
	    return Intpair.BigIntegerToLong(BigInteger.valueOf(a).pow((int) b));
	}
	
	// Lots of cases.
	//  a^b  
	//  Upper bound:
	//  max(a)^max(b) 
	//  (if a is negative, or the negative part is larger than the positive) min(a)^max(b)  or min(a)^(max(b)-1) if max(b) is odd 
	
	// Lower bound:
	// min(a)^min(b)
	
	// (if min(a) is negative) min(a)^max(b) or min(a)^(max(b)-1) (for case where max(b) is even)
	// 
	
	public Intpair getBounds() {
	    Intpair a=getChild(0).getBounds();
	    Intpair b=getChild(1).getBounds();
	    
	    long exp1=b.upper;
	    long exp2=b.upper-1;
	    long explow=b.lower;
	    // put the new bounds into b.
	    
	    b.upper=Math.max(pow(a.upper, exp1), 
	        Math.max(pow(a.lower, exp1),
	            pow(a.lower, exp2)));
	    
	    b.lower=Math.min(pow(a.lower, explow),
	        Math.min(pow(a.lower, exp1),
	            pow(a.lower, exp2)));
	    
	    return lookupBounds(b);    //  Look up in FilteredDomainStore
	}
	public PairASTNode getBoundsAST() {
	    PairASTNode a=getChild(0).getBoundsAST();
	    PairASTNode b=getChild(1).getBoundsAST();
	    
	    ASTNode exp1=b.e2;
	    ASTNode exp2=BinOp.makeBinOp("-", b.e2, new NumberConstant(1));
	    ASTNode explow=b.e1;
	    // put the new bounds into b.
	    
	    // Three cases each for upper and lower bound. 
	    
	    b.e2=new Max( new Power(a.e2, exp1), 
	        new Max( new Power(a.e1, exp1),
	            new Power(a.e1, exp2)));
	    
	    b.e1=new Min( new Power(a.e1, explow), 
	        new Min( new Power(a.e1, exp1),
	            new Power(a.e1, exp2)));
	    
	    return b;
	}
	
	public void toMinionWithAuxVar(StringBuffer b, ASTNode aux)
	{
	    b.append("pow(");
	    getChild(0).toMinion(b, false);
	    b.append(", ");
	    getChild(1).toMinion(b, false);
	    b.append(", ");
	    aux.toMinion(b, false);
	    b.append(")");
	}
	
	public void toDominionParam(StringBuffer b) {
	    b.append("Pow(");
	    getChild(0).toDominionParam(b);
	    b.append(",");
	    getChild(1).toDominionParam(b);
	    b.append(")");
	}
	public void toDominionWithAuxVar(StringBuffer b, ASTNode aux)
	{
	    b.append(CmdFlags.getCtName()+" ");
	    b.append("pow(");
	    getChild(0).toDominion(b, false);
	    b.append(", ");
	    getChild(1).toDominion(b, false);
	    b.append(", ");
	    aux.toDominion(b, false);
	    b.append(")");
	}
	public String toString() {
	    return "("+getChild(0)+"**"+getChild(1)+")";
	}
	
	public void toMinizinc(StringBuffer b,  boolean bool_context) {
	    assert(!bool_context);
	    b.append("pow(");
	    getChild(0).toMinizinc(b, bool_context);
	    b.append(",");
	    getChild(1).toMinizinc(b, bool_context);
	    b.append(")");
	}
	
	////////////////////////////////////////////////////////////////////////////
	//
	//   SAT encoding
	
    public void toSATWithAuxVar(Sat satModel, ASTNode auxVar) throws IOException {
        satModel.ternaryEncoding(this, auxVar);
    }
    
    public boolean test(long val1, long val2, long aux) {
        return pow(val1,val2)==aux;
    }
    public long func(long val1, long val2) {
        return pow(val1,val2);
    }
}