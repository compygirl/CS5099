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
import savilerow.model.SymbolTable;

public class Implies extends LogicBinOp
{
    public static final long serialVersionUID = 1L;
	public Implies(ASTNode l, ASTNode r)
	{
		super(l, r);
	}
	
	public ASTNode copy()
	{
	    return new Implies(getChild(0), getChild(1));
	}
	public boolean isRelation(){return true;}
	
	public void toMinion(StringBuffer b, boolean bool_context)
	{
	    assert bool_context;
	    b.append("ineq(");
	    getChild(0).toMinion(b, false);
	    b.append(", ");
	    getChild(1).toMinion(b, false);
	    b.append(", 0)");
	}
	public void toDominionInner(StringBuffer b, boolean bool_context)
	{
	    b.append(CmdFlags.getCtName()+" ");
	    b.append("leq(");
	    getChild(0).toDominion(b, false);
	    b.append(", ");
	    getChild(1).toDominion(b, false);
	    b.append(")");
	}
	public void toDominionParam(StringBuffer b) {
	    b.append("Implies(");
	    getChild(0).toDominionParam(b);
	    b.append(",");
	    getChild(1).toDominionParam(b);
	    b.append(")");
	}
	public void toFlatzinc(StringBuffer b, boolean bool_context) {
	    b.append("constraint bool_le(");
	    getChild(0).toFlatzinc(b, true);
	    b.append(",");
	    getChild(1).toFlatzinc(b, true);
	    b.append(");");
	}
	public void toMinizinc(StringBuffer b, boolean bool_context) {
	    b.append("(");
	    getChild(0).toMinizinc(b, true);
	    b.append("->");
	    getChild(1).toMinizinc(b, true);
	    b.append(")");
	}
	
	public ASTNode simplify() {
	    return new Or(new Negate(getChild(0)), getChild(1));  // Get rid of all implications. 
	    /*
        if(getChild(0).isConstant() && getChild(0).getValue()==1) {
            return getChild(1);
        }
        if(getChild(0).isConstant() && getChild(0).getValue()==0) {
            return new BooleanConstant(true);
        }
        
        if(getChild(0).equals(getChild(1))) return new BooleanConstant(true);
        
        if(getChild(1).isConstant() && getChild(1).getValue()==1) {
            return new BooleanConstant(true);
        }
        if(getChild(1).isConstant() && getChild(1).getValue()==0) {
            return new Negate(getChild(0));
        }
        return this;
        */
	}
	
	//  If contained in a Negate, push the negation inside using De Morgens law. 
	@Override
	public boolean isNegatable() {
	    return true;
	}
	@Override
	public ASTNode negation() {
	    return new And(getChild(0), new Negate(getChild(1)));
	}
	
	@Override
	public boolean typecheck(SymbolTable st) {
	    for(ASTNode child :  getChildren()) {
	        if(!child.typecheck(st))
	            return false;
	        if(!child.isRelation()) {
	            System.out.println("ERROR: Implication contains non-relation expression:"+child);
	            return false;
	        }
	    }
	    return true;
	}
	
	public String toString() {
	    return "("+getChild(0)+" -> "+getChild(1)+")";
	}
	
}