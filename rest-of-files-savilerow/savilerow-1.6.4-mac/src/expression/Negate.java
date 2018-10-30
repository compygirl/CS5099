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
import java.io.*;
import savilerow.model.SymbolTable;
import savilerow.*;
import savilerow.model.Sat;

// toFlatten needs to check for Gecode output.

// boolean negation.

public class Negate extends Unary
{
    public static final long serialVersionUID = 1L;
    public Negate(ASTNode a)
    {
        super(a);
    }
    
	public ASTNode copy()
	{
	    return new Negate(getChild(0));
	}
	public boolean isRelation(){return true;}
	
	public ASTNode simplify() {
	    if(getChild(0).isConstant()) {
	        if(getChild(0).getValue()==0) return new BooleanConstant(true);
	        else return new BooleanConstant(false);
	    }
	    
	    // Negate expressions that define isNegatable and negation methods. 
	    if(getChild(0).isNegatable()) {
            return getChild(0).negation();
        }
        
	    return this;
	}
	
	@Override
	public boolean isNegatable() {
	    return true;
	}
	@Override
	public ASTNode negation() {
	    return getChild(0);
	}
	
	//  CHECK FOR GECODE OUPTUT
	@Override
	public boolean toFlatten(boolean propagate) {
	    if(CmdFlags.getMiniontrans() && getChild(0) instanceof Identifier) {
	        return false;
	    }
	    return true;
	}
	
	public boolean typecheck(SymbolTable st) {
	    if(!getChild(0).typecheck(st))
	        return false;
        if(!getChild(0).isRelation()) {
            System.out.println("ERROR: Boolean negation contains numerical expression:"+this);
            return false;
        }
	    return true;
	}
	
	public void toMinion(StringBuffer b, boolean bool_context) {
	    if(bool_context) {
	        // Parent expects a constraint. 
	        b.append("w-literal(");
            getChild(0).toMinion(b, false);
            b.append(",0)");
	    }
	    else {
	        // Use Minion's negation mapper. 
	        b.append("!");
	        getChild(0).toMinion(b, false);
	    }
	}
	
	public void toFlatzinc(StringBuffer b, boolean bool_context) {
	    b.append("constraint bool_eq(");
	    getChild(0).toFlatzinc(b, true);
	    b.append(",false);");
	}
	
	public String toString()
	{
	    return "(!"+getChild(0)+")";
	}
	public void toDominionInner(StringBuffer b, boolean bool_context) {
	    b.append(CmdFlags.getCtName()+" ");
	    b.append("not(");
	    getChild(0).toDominion(b, true);
	    b.append(")");
	}
	public void toDominionParam(StringBuffer b) {
	    b.append("(!");
	    getChild(0).toDominionParam(b);
	    b.append(")");
	}
	public void toMinizinc(StringBuffer b, boolean bool_context) {
	    b.append("(not ");
	    getChild(0).toMinizinc(b, true);
	    b.append(")");
	}
	
	public Long toSATLiteral(Sat satModel) {
        if(getChild(0) instanceof SATLiteral) {
            return -((SATLiteral)getChild(0)).getLit();
        }
        else return null;
    }
	public void toSAT(Sat satModel) throws IOException
	{
		//Create a new aux var
        long auxVar = satModel.createAuxSATVariable();
        
        //Add the negation of the Aux value as a SAT clause to enforce the negation
        satModel.addClause( String.valueOf(-auxVar) );
        
        getChild(0).toSATWithAuxVar(satModel, auxVar);
	}
	
	public void toSATWithAuxVar(Sat satModel, long auxVarValue) throws IOException {
		getChild(0).toSATWithAuxVar(satModel,-auxVarValue);
	}
}
