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
import savilerow.model.*;
import savilerow.treetransformer.*;

// Join some 1-dimensional matrices into one matrix. 

public class Concatenate extends ASTNode
{
    public static final long serialVersionUID = 1L;
    public Concatenate(ArrayList<ASTNode> a) {
        super(a);
    }
    
    public ASTNode copy()
    {
        return new Concatenate(getChildren());
    }
    
    public boolean typecheck(SymbolTable st) {
        for(int i=0; i<numChildren(); i++) {
            if(! getChild(i).typecheck(st)) return false;
            if(getChild(i).getDimension()<1) {
                System.out.println("ERROR: Expected 1-dimensional or greater matrix inside concatenate: "+this);
                return false;
            }
        }
        return true;
	}
    
    public ASTNode simplify()
    {
        // Check everything is a matrix literal.
        for(int i=0; i<numChildren(); i++) {
            if( ! (getChild(i) instanceof CompoundMatrix || getChild(i) instanceof EmptyMatrix)) {
                return this;
            }
        }
        
        ArrayList<ASTNode> cm=new ArrayList<ASTNode>();
        
        for(int i=0; i<numChildren(); i++) {
            ArrayList<ASTNode> a=getChild(i).getChildren();
            a.remove(0);
            cm.addAll(a);
        }
        return CompoundMatrix.makeCompoundMatrix(cm);
    }
    
    public int getDimension() {
        if(numChildren()==0) return 1;
        return getChild(0).getDimension();
    }
    
	public String toString()
	{
	    StringBuffer b=new StringBuffer();
	    b.append("concatenate(");
	    for(int i=0; i<numChildren(); i++) {
	        b.append(getChild(i));
	        if(i<numChildren()-1) b.append(",");
	    }
	    b.append(")");
	    return b.toString();
	}
}
