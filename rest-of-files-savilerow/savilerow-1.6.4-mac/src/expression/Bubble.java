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

// Contains:
// 0 -- expression (the value of the bubble)
// 1 -- inner (constraints in the bubble -- not yet a full model.)

public class Bubble extends ASTNode
{
    public static final long serialVersionUID = 1L;
    public Bubble(ASTNode exp, ASTNode inner) {
        super(exp, inner);
    }
    
    public String toString()
    {
        return "{ "+getChild(0)+" @ "+" such that "+getChild(1)+" }";
    }
    
	public ASTNode copy()
	{
	    return new Bubble(getChild(0), getChild(1));
	}
	
	public boolean toFlatten(boolean propagate) { return false; }
	
	public boolean isNumerical() {
	    return getChild(0).isNumerical();
    }
    
    public boolean isRelation() {
        return getChild(0).isRelation();
    }
    
    @Override
    public int getDimension() {
        return getChild(0).getDimension();
    }
    
	public boolean typecheck(SymbolTable st) {
	    if(! getChild(0).typecheck(st) || ! getChild(1).typecheck(st)) {
	        return false;
	    }
	    if(! getChild(1).isRelation()) {
	        System.out.println("ERROR: In typechecking: bubble may only contain constraints on right hand side: "+this);
	        return false;
	    }
	    return true;
	}
	
	public PairASTNode getBoundsAST() {
	    return getChild(0).getBoundsAST();
	}
}
