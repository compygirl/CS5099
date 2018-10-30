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

// Intersection for sets/domains that are constant after parameter substitution.

public class Intersect extends SetBinOp
{
    public static final long serialVersionUID = 1L;
	public Intersect(ASTNode l, ASTNode r)
	{
		super(l, r);
	}
	
	public ASTNode copy()
	{
	    return new Intersect(getChild(0), getChild(1));
	}
	
	public boolean toFlatten(boolean propagate) { return false;}
	
    public ASTNode simplify() {
        if(getChild(0).getCategory()==ASTNode.Constant && getChild(1).getCategory()==ASTNode.Constant) {
            // Turn both domains into a list of pairs of longs
            
            ArrayList<Intpair> a=getChild(0).getIntervalSet();
            ArrayList<Intpair> b=getChild(1).getIntervalSet();
            
            // Absolutely must receive a and b in order!
            
            ArrayList<Intpair> out=new ArrayList<Intpair>();
            
            for(int aloc=0; aloc<a.size(); aloc++) {
                
                for(int bloc=0; bloc<b.size(); bloc++) {
                    Intpair blah=a.get(aloc).intersect(b.get(bloc));
                    if(! blah.isEmpty()) {
                        out.add(blah);
                    }
                }
            }
            
            return Intpair.makeDomain(out, this.isBooleanSet());
        }
        return this;
    }
    
    public boolean isSet() {
        return true;
    }
    public boolean isFiniteSet() {
        return this.isFiniteSetUpper() && this.isFiniteSetLower();
    }
    public boolean isFiniteSetUpper() {
        return getChild(0).isFiniteSetUpper() || getChild(1).isFiniteSetUpper();
    }
    public boolean isFiniteSetLower() {
        return getChild(0).isFiniteSetLower() || getChild(1).isFiniteSetLower();
    }
    public boolean isBooleanSet() {
        // If they are not both boolean, then the bools get cast to integers.
        return getChild(0).isBooleanSet() && getChild(1).isBooleanSet();
    }
    
    public Intpair getBounds() {
        return getChild(0).getBounds().intersect(getChild(1).getBounds());
    }
	public PairASTNode getBoundsAST() {
	    PairASTNode p1=getChild(0).getBoundsAST();
	    PairASTNode p2=getChild(1).getBoundsAST();
	    return new PairASTNode(new Max(p1.e1, p2.e1), new Min(p1.e2, p2.e2));
	}
    
    public boolean containsValue(long val) {
	    return getChild(0).containsValue(val) && getChild(1).containsValue(val);
	}
	
	public String toString() {
	    return "("+getChild(0)+" intersect "+getChild(1)+")";
	}
}
