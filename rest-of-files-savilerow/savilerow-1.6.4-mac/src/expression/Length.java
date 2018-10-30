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
import java.lang.Math;

// Takes a one-dimensional matrix and evaluates to the number of elements of that
// matrix. 

public class Length extends ASTNode
{
    public static final long serialVersionUID = 1L;
	public Length(ASTNode a)
	{
		super(a);
	}
	
	public ASTNode copy() {
	    return new Length(getChild(0));
	}
	
	public boolean toFlatten(boolean propagate) { return false; }
	public boolean isNumerical() {
        return true;
    }
    
    @Override
    public boolean typecheck(SymbolTable st) {
        if(!getChild(0).typecheck(st))
	        return false;
        
        if(getChild(0).getDimension()!=1) {
            System.out.println("ERROR: Length must contain a one-dimensional matrix:"+this); 
            return false;
        }
        
        return true;
    }
    
    public Intpair getBounds() {
        return new Intpair(0L, Long.MAX_VALUE);
    }
    public PairASTNode getBoundsAST() {
	    return new PairASTNode(new NumberConstant(0), new PosInfinity());	    
	}
	
	public ASTNode simplify() {
	    ArrayList<ASTNode> idxdoms=getChild(0).getIndexDomains();
	    
	    if(idxdoms==null) {
	        // Can't evaluate this yet. 
	        return this;
	    }
	    
	    assert idxdoms.size()==1;
	    
	    ArrayList<Intpair> intervals=idxdoms.get(0).getIntervalSet();
	    long numvals=0;
	    for(int i=0; i<intervals.size(); i++) {
	        numvals+=intervals.get(i).upper-intervals.get(i).lower+1;
	    }
	    
	    return new NumberConstant(numvals);
	}
	
	public String toString() {
	    return "length("+getChild(0)+")";
	}
}
