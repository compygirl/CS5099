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
import savilerow.model.*;

// This is needed so that an empty matrix can have a dimension and type (int or bool)

public class EmptyMatrix extends ASTNode
{
    public static final long serialVersionUID = 1L;
    // Child 0 is a matrix domain.  this allows us to store the number of dimensions and 
    // the base type in a way that is consistent with other places.  
    
    public EmptyMatrix(ASTNode type) {
        super(type);
        assert type instanceof MatrixDomain;
    }
    
    public boolean isRelation() {
        // Check the base domain. 
        return getChild(0).getChild(0).isBooleanSet();
    }
    
    public boolean isNumerical() {
        return ! (getChild(0).getChild(0).isBooleanSet());
    }
    
    public ASTNode copy() {
	    return new EmptyMatrix(getChild(0));
	}
	
	public boolean toFlatten(boolean propagate) { return false; }
	
	// Use the bounds of the base domain.
	public Intpair getBounds() {
	    return getChild(0).getChild(0).getBounds();
	}
	public PairASTNode getBoundsAST() {
	    return getChild(0).getChild(0).getBoundsAST();
	}
	
	// Just get dimension of the matrix domain. 
	public int getDimension() {
	    return getChild(0).numChildren()-3;
	}
	
	public ArrayList<ASTNode> getIndexDomains() {
	    ArrayList<ASTNode> tmp=getChild(0).getChildren();
	    tmp.remove(0); tmp.remove(0); tmp.remove(0);
	    return tmp;
	}
	
	// ALL output methods except E' drop the dimension and base type. 
	public void toMinion(StringBuffer b, boolean bool_context)
	{
        b.append("[]");
	}
	public void toDominionInner(StringBuffer b, boolean bool_context){
        b.append("[]");
	}
	public void toDominionParam(StringBuffer b){
        b.append("[]");
	}
	
	public void toFlatzinc(StringBuffer b, boolean bool_context) {
        b.append("[]");
	}
	
	public void toMinizinc(StringBuffer b, boolean bool_context) {
        b.append("[]");
	}
	
	public String toString() {
	    return "([] : `"+getChild(0)+"`)";
	}
	
}
