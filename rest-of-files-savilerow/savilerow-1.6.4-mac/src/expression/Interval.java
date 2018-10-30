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

// This is a special type for use only in doing some symbolic manipulation in
// TransformToFlatClass. Very similar to Range with the exception of the
// getBoundsAST method. 

public class Interval extends ASTNode
{
    public static final long serialVersionUID = 1L;
    public Interval(ASTNode l, ASTNode r)
    {
        super(l,r);
    }
    
    public ASTNode copy()
	{
	    return new Interval(getChild(0), getChild(1));
	}
	
	public PairASTNode getBoundsAST() {
	    return new PairASTNode(getChild(0).getBoundsAST().e1, getChild(1).getBoundsAST().e2);
	}
}
