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

// Flatten the top dimension of a matrix. 

public class ConcatenateMatrix extends ASTNode
{
    public static final long serialVersionUID = 1L;
    public ConcatenateMatrix(ASTNode a) {
        super(a);
    }
    
    public ASTNode copy() {
        return new ConcatenateMatrix(getChild(0));
    }
    
    public boolean typecheck(SymbolTable st) {
        if(! getChild(0).typecheck(st)) return false;
        if(getChild(0).getDimension()<2) {
            System.out.println("ERROR: Expected 2-dimensional or greater matrix inside concatenate: "+this);
            return false;
        }
        return true;
	}
    
    public ASTNode simplify()
    {
        if(getChild(0) instanceof CompoundMatrix || getChild(0) instanceof EmptyMatrix) {
            ArrayList<ASTNode> tmp=getChild(0).getChildren();
            tmp.remove(0);
            return new Concatenate(tmp);
        }
        return this;
    }
    
    public int getDimension() {
        return getChild(0).getDimension()-1;
    }
    
	public String toString()
	{
	    StringBuffer b=new StringBuffer();
	    b.append("concatenate(");
	    b.append(getChild(0));
	    b.append(")");
	    return b.toString();
	}
}
