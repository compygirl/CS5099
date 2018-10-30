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

public class Iff extends LogicBinOp
{
    public static final long serialVersionUID = 1L;
	public Iff(ASTNode l, ASTNode r)
	{
		super(l, r);
	}
	
	public ASTNode copy()
	{
	    return new Iff(getChild(0), getChild(1));
	}
	public boolean isRelation(){return true;}
	
	public ASTNode simplify() {
	    
	    // One child is a constant.
	    if(getChild(0).isConstant() && getChild(0).getValue()==1) {
	        return getChild(1);
	    }
	    if(getChild(1).isConstant() && getChild(1).getValue()==1) {
	        return getChild(0);
	    }
	    if(getChild(0).isConstant() && getChild(0).getValue()==0) {
	        return new Negate(getChild(1));
	    }
	    if(getChild(1).isConstant() && getChild(1).getValue()==0) {
	        return new Negate(getChild(0));
	    }
	    
	    // Both children constant
	    if(getChild(0).isConstant() && getChild(1).isConstant()) {
	        return new BooleanConstant(getChild(0).getValue()==getChild(1).getValue());
	    }
	    
	    if(getChild(0).equals(getChild(1))) {  // If symbolically equal, return true.
	        return new BooleanConstant(true);
	    }
	    
	    // If one side is the negation of the other, return false.  
	    // Prevents deletevars unifying  x<->not(x) and causing infinite recursion of replacing x with not(x). 
	    if(getChild(0) instanceof Negate && getChild(0).getChild(0).equals(getChild(1))) {
	        return new BooleanConstant(false);
	    }
	    if(getChild(1) instanceof Negate && getChild(1).getChild(0).equals(getChild(0))) {
	        return new BooleanConstant(false);
	    }
	    
	    return this;
	}
	
	//  If contained in a Negate, push the negation inside 
	@Override
	public boolean isNegatable() {
	    return true;
	}
	@Override
	public ASTNode negation() {
	    // If one child is an identifier, negate that one.
	    if(getChild(0) instanceof Identifier) return new Iff(new Negate(getChild(0)), getChild(1));
	    if(getChild(1) instanceof Identifier) return new Iff(getChild(0), new Negate(getChild(1)));
	    
	    // Try to negate a side that can have the negation pushed further in. 
	    if(getChild(0).isNegatable()) return new Iff(new Negate(getChild(0)), getChild(1));
	    return new Iff(getChild(0), new Negate(getChild(1)));
	}
	
	@Override
	public ASTNode normalise() {
	    if(getChild(0).hashCode()>getChild(1).hashCode()) {
	        return new Iff(getChild(1), getChild(0));
	    }
	    return this;
	}
	@Override
	public boolean typecheck(SymbolTable st) {
	    for(ASTNode child :  getChildren()) {
	        if(!child.typecheck(st))
	            return false;
	        if(!child.isRelation()) {
	            System.out.println("ERROR: Iff contains non-relation expression:"+child);
	            return false;
	        }
	    }
	    return true;
	}
	
	public void toMinion(StringBuffer b, boolean bool_context)
	{
	    assert bool_context;
	    b.append("eq(");
	    getChild(0).toMinion(b, false);
	    b.append(", ");
	    getChild(1).toMinion(b, false);
	    b.append(")");
	}
	public void toDominionInner(StringBuffer b, boolean bool_context)
	{
	    b.append(CmdFlags.getCtName()+" ");
	    b.append("eq(");
	    getChild(0).toDominion(b, false);
	    b.append(", ");
	    getChild(1).toDominion(b, false);
	    b.append(")");
	}
	public void toDominionParam(StringBuffer b) {
	    b.append("(");
	    getChild(0).toDominionParam(b);
	    b.append("=");
	    getChild(1).toDominionParam(b);
	    b.append(")");
	}
	public void toFlatzinc(StringBuffer b, boolean bool_context) {
	    b.append("constraint bool_eq(");
	    getChild(0).toFlatzinc(b, true);
	    b.append(",");
	    getChild(1).toFlatzinc(b, true);
	    b.append(");");
	}
	public void toMinizinc(StringBuffer b, boolean bool_context) {
	    b.append("(");
	    getChild(0).toMinizinc(b, true);
	    b.append("<->");
	    getChild(1).toMinizinc(b, true);
	    b.append(")");
	}
	
	//  Identical to Equals.
	public Long toSATLiteral(Sat satModel) {
	    if(getChild(0).isConstant()) {
	        return getChild(1).directEncode(satModel, getChild(0).getValue());
        }
        if(getChild(1).isConstant()) {
            return getChild(0).directEncode(satModel, getChild(1).getValue());
        }
        return null;
	}
	
	public void toSAT(Sat satModel) throws IOException
	{
	    if(getChild(0) instanceof SATLiteral) {
	        getChild(1).toSATWithAuxVar(satModel, ((SATLiteral)getChild(0)).getLit());
	    }
	    else if(getChild(1) instanceof SATLiteral) {
	        getChild(0).toSATWithAuxVar(satModel, ((SATLiteral)getChild(1)).getLit());
	    }
	    else {
            long satVar=satModel.createAuxSATVariable();
            
            getChild(0).toSATWithAuxVar(satModel, satVar);
            getChild(1).toSATWithAuxVar(satModel, satVar);
        }
	}

	public void toSATWithAuxVar(Sat satModel, long auxVarValue) throws IOException {
		long identifierValue1=satModel.createAuxSATVariable();
    	long identifierValue2=satModel.createAuxSATVariable();
    	
		//System.out.println("AUX VAR VALUE " + auxVarValue);
		getChild(0).toSATWithAuxVar(satModel,identifierValue1);
		getChild(1).toSATWithAuxVar(satModel,identifierValue2);
		
		//Encode the conflict clauses
		String clause1=(-auxVarValue) + " " + (-identifierValue1) + " " + (identifierValue2);
		String clause2=(-auxVarValue) + " " + (identifierValue1) + " " + (-identifierValue2);
		
		//Encode the support clauses
		String clause3=(auxVarValue) + " " + (identifierValue1) + " " + identifierValue2;
		String clause4=(auxVarValue) + " " + (-identifierValue1) + " " +(-identifierValue2);
		
		satModel.addClause(clause1);
		satModel.addClause(clause2);
		satModel.addClause(clause3);
		satModel.addClause(clause4);
	}
	
	public String toString() {
	    return "("+getChild(0)+" <-> "+getChild(1)+")";
	}

    public boolean childrenAreSymmetric() {
        return true;
    }
}
