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
import savilerow.model.SymbolTable;

// Union for sets that are constant after parameter substitution.
// syntax: int(blah blah) union int(blah blah blah)
// set ops all left associative and intersect binds tighter than union

public class Union extends SetBinOp
{
    public static final long serialVersionUID = 1L;
	public Union(ASTNode l, ASTNode r)
	{
		super(l, r);
	}
	
	public ASTNode copy()
	{
	    return new Union(getChild(0), getChild(1));
	}
	
	public boolean toFlatten(boolean propagate) { return false;}
	
    public ASTNode simplify() {
        if(getChild(0).getCategory()==ASTNode.Constant && getChild(1).getCategory()==ASTNode.Constant) {
            
            // Turn both domains into a list of pairs of longs
            
            ArrayList<Intpair> a=getChild(0).getIntervalSet();
            ArrayList<Intpair> b=getChild(1).getIntervalSet();
            
            a.addAll(b);
            
            Collections.sort(a);
            
            // Now compare pairs to their neighbours and merge if overlapping.
            for(int i=0; i<a.size()-1; i++) {
                Intpair tmp=a.get(i).merge(a.get(i+1));
                if(tmp!=null) {
                    a.set(i, tmp);
                    a.remove(i+1);
                    i--;
                }
            }
            
            return Intpair.makeDomain(a, this.isBooleanSet());
        }
        return this;
    }
    public boolean isSet() {
        return true;
    }
    public boolean isFiniteSet() {
        return getChild(0).isFiniteSet() && getChild(1).isFiniteSet();
    }
    public boolean isFiniteSetUpper() {
        return getChild(0).isFiniteSetUpper() && getChild(1).isFiniteSetUpper();
    }
    public boolean isFiniteSetLower() {
        return getChild(0).isFiniteSetLower() && getChild(1).isFiniteSetLower();
    }
    public boolean isBooleanSet() {
        // If they are not both boolean, then the bools get cast to integers.
        return getChild(0).isBooleanSet() && getChild(1).isBooleanSet();
    }
    public Intpair getBounds() {
        return getChild(0).getBounds().union(getChild(1).getBounds());
    }
	public PairASTNode getBoundsAST() {
	    PairASTNode p1=getChild(0).getBoundsAST();
	    PairASTNode p2=getChild(1).getBoundsAST();
	    return new PairASTNode(new Min(p1.e1, p2.e1), new Max(p1.e2, p2.e2));
	}
	
	public boolean containsValue(long val) {
	    return getChild(0).containsValue(val) || getChild(1).containsValue(val);
	}
	
	public String toString() {
	    return "("+getChild(0)+" union "+getChild(1)+")";
	}
}
