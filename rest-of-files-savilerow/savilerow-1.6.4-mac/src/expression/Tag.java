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


// Tag wraps an ASTNode to mark it.
// Used in Transform Matrix Indices to avoid transforming the same term twice. 

// If disappear_when_simplify is set to true, the tag deletes itself next time
// a simplify pass is run. 

public class Tag extends ASTNode
{
    public static final long serialVersionUID = 1L;
    boolean disappear_when_simplify;
    public Tag(ASTNode in, boolean disappear)
    {
        super(in);
        disappear_when_simplify=disappear;
    }
    
	public ASTNode copy()
	{
	    return new Tag(getChild(0), disappear_when_simplify);
	}
	
	public ASTNode simplify() {
	    
	    if(disappear_when_simplify) return getChild(0);
	    return this;
	}
	
	@Override
	public int getDimension() {
	    return getChild(0).getDimension();
	}
	
	public boolean isRelation() {
	    return getChild(0).isRelation();
	}
	public boolean isNumerical() {
	    return getChild(0).isNumerical();
	}
	
	public Intpair getBounds() {
	    return getChild(0).getBounds();
	}
	public PairASTNode getBoundsAST() {
	    return getChild(0).getBoundsAST();
	}
}
