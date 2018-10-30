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
import savilerow.model.*;
import java.util.*;

// Element(matrix or matrix slice, index expression) is a function to the result.
// This one has default value 0 for out of range. Indexed from 1.

public class SafeElementOne extends ASTNode
{
    public static final long serialVersionUID = 1L;
    public SafeElementOne(ASTNode arr, ASTNode ind)
    {
        super(arr, ind);
    }
    
	public ASTNode copy()
	{
	    return new SafeElementOne(getChild(0), getChild(1));
	}
	
	public boolean isRelation(){
	    return getChild(0).isRelation();}
	public boolean isNumerical() {
        return getChild(0).isNumerical();}
    
	public boolean toFlatten(boolean propagate){return true;}
	
	public ASTNode simplify() {
	    // Turn into an ElementOne.
	    
	    if(getChild(0) instanceof CompoundMatrix || getChild(0) instanceof EmptyMatrix) {
	        // Turn it into ElementOne for output to Gecode
	        Intpair idxbnds=getChild(1).getBounds();
	        if(idxbnds.lower>=1 && idxbnds.upper<=getChild(0).numChildren()-1) {
	            return new ElementOne(getChild(0), getChild(1));
            }
            else {
                // Make a new Mapping. 
                HashMap<Long, Long> map=new HashMap<Long, Long>();
                for(long domval=1; domval<=getChild(0).numChildren()-1; domval++) {
                    map.put(domval, domval);
                }
                ASTNode mapping=new Mapping(map, getChild(1));
                // Default value of mapping will be getChild(0).numChildren()  so add one more
                // element to child 0. 
                ArrayList<ASTNode> ch=getChild(0).getChildren();
                ch.remove(0);
                ch.add(new NumberConstant(0));  // Default value of the element.
                return new ElementOne(CompoundMatrix.makeCompoundMatrix(ch), mapping);
            }
        }
        
	    return this;
	}
	
	public Intpair getBounds() {
	    Intpair a = getChild(0).getBounds();
	    if(a.lower>0) a.lower=0;    //  Add default value into range.
	    if(a.upper<0) a.upper=0;
	    return lookupBounds(a);    //  Look up in FilteredDomainStore
	}
	public PairASTNode getBoundsAST() {
	    PairASTNode a= getChild(0).getBoundsAST();
	    a.e1=new Min(a.e1, new NumberConstant(0));   /// Add 0 into range, either below or above. 
	    a.e2=new Max(a.e2, new NumberConstant(0));
	    return a;
	}
}
