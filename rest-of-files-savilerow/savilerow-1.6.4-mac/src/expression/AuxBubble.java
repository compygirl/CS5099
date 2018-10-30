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


import savilerow.model.SymbolTable;

import java.util.*;

// A bubble e.g. { x @ find x : int(1..10) such that  x=y+z }
// Must contain a single identifier (0-dimensional) on the left
// Model on the right has no givens, one find for the same id.
// Constraint must be functional for the id. (THIS IS NOT CHECKED)

// Hence it can be flattened like any expression.

// Contains:
// 0 -- id
// 1 -- find statement
// 2 -- constraint or And of constraints.


public class AuxBubble extends ASTNode
{
    public static final long serialVersionUID = 1L;
    public AuxBubble(ASTNode id, ASTNode find, ASTNode cts) {
        super(id,find,cts);
    }
    
    public String toString()
    {
        return "{ "+getChild(0)+" @ "+getChild(1)+" such that "+getChild(2)+" }";
    }
    
	public ASTNode copy()
	{
	    return new AuxBubble(getChild(0), getChild(1), getChild(2));
	}
	
	public boolean toFlatten(boolean propagate) { return true; }
	
	public boolean isNumerical() {
	    // Dig into the find statement
        ASTNode dom=getChild(1).getChild(1);
        return !(dom instanceof BooleanDomain);
    }
    
    public boolean isRelation() {
        // Dig into the find statement
        ASTNode dom=getChild(1).getChild(1);
        return (dom instanceof BooleanDomain);
    }
    
	public boolean typecheck(SymbolTable st) {
	    // Avoid the undefined identifier on the left. 
	    return getChild(1).typecheck(st) && getChild(2).typecheck(st);
	    // Should do more checks here.
	}
	
	
    @Override
    public ASTNode getDomainForId(ASTNode id) {
        if(getChild(0).equals(id)) {
            return getChild(1).getChild(1);   // domain from find statement.
        }
        else {
            if(getParent()==null) return null;
            return getParent().getDomainForId(id);  // continue up the tree.
        }
    }
    
	
	public PairASTNode getBoundsAST() {
	    return getChild(1).getChild(1).getBoundsAST();
	}
	
}
