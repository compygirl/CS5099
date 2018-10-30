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

// Cast 1-d matrix (non-decision) into a set. 

import java.util.*;
import savilerow.model.*;

public class ToSet extends ASTNode
{
    public static final long serialVersionUID = 1L;
	public ToSet(ASTNode m)
	{
		super(m);
	}
	
	public ASTNode copy() {
	    return new ToSet(getChild(0));
	}
	
	public boolean toFlatten(boolean propagate) { return false; }
	public boolean isNumerical() {
        return false;
    }
    public boolean isSet() {
        return true;
    }
    public boolean isFiniteSet() {
        return true;
    }
    public boolean isFiniteSetUpper() {
        return true;
    }
    public boolean isFiniteSetLower() {
        return true;
    }
    public boolean isBooleanSet() {
        return getChild(0).isRelation();
    }
    public boolean typecheck(SymbolTable st) {
        if(! getChild(0).typecheck(st)) return false;
        if(getChild(0).getDimension()!=1) {
            System.out.println("ERROR: Expected one-dimensional matrix inside toSet function: "+this);
            return false;
        }
        if(getChild(0).getCategory()==ASTNode.Decision) {
            System.out.println("ERROR: toSet function contains a decision variable: "+this);
            return false;
        }
        return true;
	}
    public ASTNode simplify() {
        ASTNode mat=getChild(0);
        
        if(mat instanceof Identifier) {
            mat = ((Identifier) mat).global_symbols.getConstantMatrix(mat.toString());
        }
        
        if( (mat instanceof EmptyMatrix || mat instanceof CompoundMatrix)
            && mat.getCategory() == ASTNode.Constant) {
            ArrayList<Long> tmp=new ArrayList<Long>();
            for(int i=1; i<mat.numChildren(); i++) {
                tmp.add(mat.getChild(i).getValue());
            }
            
            Collections.sort(tmp);
            
            if(! mat.isRelation()) {
                ArrayList<ASTNode> tmp2=new ArrayList<ASTNode>();
                
                for(int i=0; i<tmp.size(); i++) {
                    if(i==0 || ! tmp.get(i).equals(tmp.get(i-1))) {
                        // If not same as previous entry, add to set
                        tmp2.add(new NumberConstant(tmp.get(i)));
                    }
                }
                return new IntegerDomain(tmp2);
            }
            else {
                if(tmp.size()==0) {
                    return new BooleanDomain(new EmptyRange());
                }
                else {
                    return new BooleanDomain(new Range(new NumberConstant(tmp.get(0)), new NumberConstant(tmp.get(tmp.size()-1))));
                }
            }
        }
	    return this;
	}
	
	public Intpair getBounds() {
	    return getChild(0).getBounds();
	}
	public PairASTNode getBoundsAST() {
	    return getChild(0).getBoundsAST();  // Bounds of a matrix enclose all values in the matrix. 
	}
    
	public String toString() {
	    return "toSet("+getChild(0)+")";
	}
}