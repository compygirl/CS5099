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

public class Less extends BinOp
{
    public static final long serialVersionUID = 1L;
	public Less(ASTNode l, ASTNode r)
	{
		super(l, r);
	}
	
	public ASTNode copy()
	{
	    return new Less(getChild(0), getChild(1));
	}
	public boolean isRelation(){return true;}
	
	public ASTNode simplify() {
	    
	    if(getChild(0).equals(getChild(1))) return new BooleanConstant(false);
	    
	    Intpair a=getChild(0).getBounds();
	    Intpair b=getChild(1).getBounds();
	    
	    if(a.upper<b.lower) return new BooleanConstant(true);
	    if(a.lower >= b.upper) return new BooleanConstant(false);
	    
	    if(getChild(0).isConstant() && getChild(1).isConstant()) {
	        if(getChild(0).getValue()<getChild(1).getValue()) return new BooleanConstant(true);
	        else return new BooleanConstant(false);
	    }
	    
	    // Now simplify sum1<sum2 to sum1-sum2<=-1
	    // Changes to <= because that is the standard way of expressing sums. 
	    if(getChild(0) instanceof WeightedSum && getChild(1) instanceof WeightedSum) {
	        return new LessEqual(BinOp.makeBinOp("-", getChild(0), getChild(1)), new NumberConstant(-1));
	    }
	    
	    // Finally it helps identical CSE if sums have no constants in them. 
	    // Shift the constant to the other side. 
	    if(getChild(0) instanceof WeightedSum && getChild(0).getCategory()==ASTNode.Decision && getChild(1).getCategory()<ASTNode.Decision) {
	        Pair<ASTNode, ASTNode> p1=((WeightedSum)getChild(0)).retrieveConstant();
	        if(p1!=null) {
	            return new Less(p1.getSecond(), BinOp.makeBinOp("-", getChild(1), p1.getFirst()));
	        }
	        else {
	            return new LessEqual(getChild(0), BinOp.makeBinOp("-", getChild(1), new NumberConstant(1)));
	        }
	    }
	    if(getChild(1) instanceof WeightedSum && getChild(1).getCategory()==ASTNode.Decision && getChild(0).getCategory()<ASTNode.Decision) {
	        Pair<ASTNode, ASTNode> p1=((WeightedSum)getChild(1)).retrieveConstant();
	        if(p1!=null) {
	            return new Less(BinOp.makeBinOp("-", getChild(0), p1.getFirst()), p1.getSecond());
	        }
	        else {
	            return new LessEqual(BinOp.makeBinOp("+", getChild(0), new NumberConstant(1)), getChild(1));
	        }
	    }
	    
	    /*
	    // Post-1.6 part, turn x<5 into x!=5 if 5 is the top of the domain. 
	    if(getChild(0) instanceof Identifier && getChild(1).isConstant() && a.e2.equals(getChild(1))) {
	        return new AllDifferent(getChild(0), getChild(1));
	    }
	    // 1<x where 1 is min(x) ---> x!=1
	    if(getChild(1) instanceof Identifier && getChild(0).isConstant() && b.e1.equals(getChild(0))) {
	        return new AllDifferent(getChild(0), getChild(1));
	    }
	    // Also two cases where it turns into equality. 
	    */
	    
	    return this;
	}
	
	@Override
	public boolean isNegatable() {
	    return true;
	}
	@Override
	public ASTNode negation() {
	    return new LessEqual(getChild(1), getChild(0));
	}
	
	public void toMinion(StringBuffer b, boolean bool_context)
	{
	    assert bool_context;
	    b.append("ineq(");
	    getChild(0).toMinion(b, false);
	    b.append(", ");
	    getChild(1).toMinion(b, false);
	    b.append(", -1)");
	}
	
	public String toString() {
	    return "("+getChild(0)+"<"+getChild(1)+")";
	}
	public void toDominionParam(StringBuffer b) {
	    b.append("(");
	    getChild(0).toDominionParam(b);
	    b.append("<");
	    getChild(1).toDominionParam(b);
	    b.append(")");
	    
	}
	public void toDominionInner(StringBuffer b, boolean bool_context) {
	    b.append(CmdFlags.getCtName()+" ");
	    b.append("less(");
	    getChild(0).toDominion(b, false);
	    b.append(", ");
	    getChild(1).toDominion(b, false);
	    b.append(")");
	}
	public void toFlatzinc(StringBuffer b, boolean bool_context) {
	    b.append("constraint int_lt(");
	    getChild(0).toFlatzinc(b, false);
	    b.append(",");
	    getChild(1).toFlatzinc(b, false);
	    b.append(");");
	}
	public void toMinizinc(StringBuffer b, boolean bool_context) {
	    b.append("(");
	    getChild(0).toMinizinc(b, false);
	    b.append("<");
	    getChild(1).toMinizinc(b, false);
	    b.append(")");
	}
	
	////////////////////////////////////////////////////////////////////////////
	//  SAT encodings
	
	//  Support encoding seems better than order and direct encodings, in informal experiment with Black Hole
	
	public Long toSATLiteral(Sat satModel) {
	    if(getChild(0).isConstant()) {
	        if(getChild(1) instanceof SATLiteral) {
	            assert getChild(0).getValue()==0;   // The only sensible value. If it's anything else, simplify is incomplete.
	            return ((SATLiteral)getChild(1)).getLit();  // 0<b  rewrites to b
	        }
	        else {
	            assert getChild(1) instanceof Identifier;
	            return -satModel.getOrderVariable(getChild(1).toString(), getChild(0).getValue());
	        }
        }
        if(getChild(1).isConstant()) {
            if(getChild(0) instanceof SATLiteral) {
	            assert getChild(1).getValue()==1;
	            return -((SATLiteral)getChild(0)).getLit();  // b<1  rewrites to -b
	        }
	        else {
	            assert getChild(0) instanceof Identifier;
	            return satModel.getOrderVariable(getChild(0).toString(), getChild(1).getValue()-1);
	        }
        }
        return null;
	}
	
    public void toSAT(Sat satModel) throws IOException {
        satModel.supportEncodingBinary(this,getChild(0),getChild(1));
    }
    
    public void toSATWithAuxVar(Sat satModel, long auxVarValue) throws IOException {
        satModel.supportEncodingBinaryWithAuxVar(this,getChild(0),getChild(1),auxVarValue);
    }

    public boolean test(long value1, long value2)
    {
        return value1<value2;
    }
}