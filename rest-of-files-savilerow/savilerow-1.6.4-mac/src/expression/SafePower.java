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
import java.lang.Math;
import savilerow.model.Sat;

public class SafePower extends BinOp
{
    public static final long serialVersionUID = 1L;
	public SafePower(ASTNode l, ASTNode r)
	{
		super(l, r);
	}
	
	public ASTNode copy() {
	    return new SafePower(getChild(0), getChild(1));
	}
	
	public boolean toFlatten(boolean propagate) { return true; }
	public boolean isNumerical() {
        return true;
    }
    
	public ASTNode simplify() {
	    if(getChild(0).isConstant() && getChild(1).isConstant()) {
	        long a=getChild(0).getValue();
	        long b=getChild(1).getValue();
	        if((a==0 && b==0) || b<0) {
	            return new NumberConstant(0);
	        }
	        return new NumberConstant(Power.pow(a,b));
	    }
	    if(getChild(1).isConstant() && getChild(1).getValue()==1) {
	        return getChild(0);
	    }
	    
	    return this;
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
	    
	    b.upper=Math.max( Power.pow(a.upper, exp1), 
	        Math.max(Power.pow(a.lower, exp1),
	            Power.pow(a.lower, exp2)));
	    
	    b.lower=Math.min( Power.pow(a.lower, explow),
	        Math.min(Power.pow(a.lower, exp1),
	            Power.pow(a.lower, exp2)));
	    
	    // Add zero for undef
	    if(b.upper < 0) b.upper=0;
	    if(b.lower > 0) b.lower=0;
	    
	    // If target is Minion, will use normal power ct which defines 0^0 as 1. 
	    // So add 1 as default value. 
	    if(b.upper < 1) b.upper=1;
	    if(b.lower > 1) b.lower=1;
	    
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
	    
	    b.e2=new Max( new SafePower(a.e2, exp1), 
	        new Max( new SafePower(a.e1, exp1),
	            new SafePower(a.e1, exp2)));
	    
	    b.e1=new Min( new SafePower(a.e1, explow), 
	        new Min( new SafePower(a.e1, exp1),
	            new SafePower(a.e1, exp2)));
	    
	    // Add zero for undef
	    b.e2=new Max( b.e2, new NumberConstant(0));
	    b.e1=new Min( b.e1, new NumberConstant(0));
	    
	    // Add one for undef for Minion
	    b.e2=new Max( b.e2, new NumberConstant(1));
	    b.e1=new Min( b.e1, new NumberConstant(1));
	    
	    return b;
	}
	/* 
	public long getValue() {
	    assert getChild(0).isConstant() && getChild(1).isConstant();
	    return (int) Math.pow(getChild(0).getValue(), getChild(1).getValue());
	}*/
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
	
	// WARNING -- rest of these drop 'safe' default value.
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
        return func(val1, val2)==aux;
    }
    public long func(long val1, long val2) {
        if((val1==0 && val2==0) || val2<0) {
            return 0;
        }
        return Power.pow(val1,val2);
    }
}