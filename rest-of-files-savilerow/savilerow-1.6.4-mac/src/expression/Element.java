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
// Indexes from 0. 

public class Element extends ASTNode
{
    public static final long serialVersionUID = 1L;
    public Element(ASTNode arr, ASTNode ind) {
        super(arr, ind);
    }
    
	public ASTNode copy()
	{
	    return new Element(getChild(0), getChild(1));
	}
	
	public boolean isRelation(){
	    return getChild(0).isRelation();}
	public boolean isNumerical() {
        return getChild(0).isNumerical();}
    
	public boolean toFlatten(boolean propagate){return true;}
	
	// Why is this here? Element is not in the input language.
	public boolean typecheck(SymbolTable st) {
	    for(int i=0; i<2; i++) { if(!getChild(i).typecheck(st)) return false; }
        if(getChild(0).getDimension()!=1) {
            CmdFlags.println("ERROR: Expected one-dimensional matrix for first argument of element constraint: "+this);
            return false;
        }
        return true;
    }
    
    public ASTNode simplify() {
        // if getChild(0) is an EmptyMatrix then index must be out of bounds -- so do nothing. 
        
        if(getChild(0) instanceof CompoundMatrix) {
            if(getChild(1).isConstant()) {     // If the index is a constant, evaluate it. 
                long idx=getChild(1).getValue();
                if(idx<0 || idx>=(getChild(0).numChildren()-1)) {
                    return this;   // out of bounds -- do not attempt to evaluate it. 
                }
                else {
                    return getChild(0).getChild( (int)idx+1 );  // Index from 0. Index domain is in position 0. 
                }
            }
            
            Intpair a=getChild(1).getBounds();
            int numelements=getChild(0).numChildren()-1;
            
            if(a.upper<numelements-1) {
                // Always safe to trim the right-hand end of the matrix
                ArrayList<ASTNode> newelements=list();
                
                for(int i=1; i<=a.upper+1; i++) newelements.add(getChild(0).getChild(i));
                return new Element(CompoundMatrix.makeCompoundMatrix(newelements), getChild(1));
            }
            
            //  IF the index is not yet flattened, then trim both ends.
            if(getChild(1).toFlatten(false) && (a.lower>0 || a.upper<numelements-1)) {
                // Trim the ends of the compound matrix by the bounds of the index variable. 
                // Does not deal with holes in the index domain. This would require a table constraint.     
                // Potentially unflattens the index variable. dangerous. 
                ArrayList<ASTNode> newelements=list();
                
                if(a.lower<0) a.lower=0;
                if(a.upper>numelements-1) a.upper=numelements-1;
                
                for(int i=(int)a.lower; i<=a.upper; i++) newelements.add(getChild(0).getChild(i+1));
                return new Element(CompoundMatrix.makeCompoundMatrix(newelements), BinOp.makeBinOp("-", getChild(1), new NumberConstant(a.lower)));
            }
	    }
	    
	    return this;
	}
	
	public Intpair getBounds() {
	    Intpair a=getChild(0).getBounds();
	    return lookupBounds(a);    //  Look up in FilteredDomainStore
	}
	public PairASTNode getBoundsAST() {
	    return getChild(0).getBoundsAST();
	}
	
	public void toMinionWithAuxVar(StringBuffer b, ASTNode aux)
	{
	    // Might need to use an element rather than watchelement.
	    if(CmdFlags.getUseBoundVars() && 
	        (aux.exceedsBoundThreshold() || getChild(0).exceedsBoundThreshold() || getChild(1).exceedsBoundThreshold() )) {
	        b.append("element(");
	    }
	    else {
	        b.append("watchelement(");
	    }
	    
	    getChild(0).toMinion(b, false);
	    b.append(", ");
	    getChild(1).toMinion(b, false);
	    b.append(", ");
	    aux.toMinion(b, false);
	    b.append(")");
	}
	public void toDominionWithAuxVar(StringBuffer b, ASTNode aux)
	{
	    b.append(CmdFlags.getCtName()+" ");
	    b.append("element(flatten(");
	    getChild(0).toDominion(b, false);
	    b.append("), ");
	    getChild(1).toDominion(b, false);
	    b.append(", ");
	    aux.toDominion(b, false);
	    b.append(")");
	}
}
