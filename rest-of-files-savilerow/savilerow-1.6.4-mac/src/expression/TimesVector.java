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

// Turns into a Times at the first opportunity.

public class TimesVector extends ASTNode
{
    public static final long serialVersionUID = 1L;
	public TimesVector(ASTNode a)
	{
		super(a);
	}
	
	public ASTNode copy()
	{
	    return new TimesVector(getChild(0));
	}
	
	public boolean typecheck(SymbolTable st) {
	    if(!getChild(0).typecheck(st))
            return false;
        if(getChild(0).getDimension()!=1) {
            CmdFlags.println("ERROR: Expected one-dimensional matrix in product function: "+this);
            return false;
        }
	    return true;
	}
	
	public ASTNode simplify()
	{
	    if(getChild(0) instanceof CompoundMatrix || getChild(0) instanceof EmptyMatrix) {
	        ArrayList<ASTNode> contents=getChild(0).getChildren();
	        contents.remove(0);
	        return new Times(contents);
	    }
	    return this;
	}
	
	public Intpair getBounds() {
	    return new Intpair(Long.MIN_VALUE, Long.MAX_VALUE);
	}
	public PairASTNode getBoundsAST() {
	    return new PairASTNode(new NegInfinity(), new PosInfinity());
	}
	
	public boolean isNumerical() {
        return true;
    }
    
	public String toString() {
	    return "product("+getChild(0)+")";
	}
}