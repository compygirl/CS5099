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
// Represents a Find statement with one id and a domain.

public class Find extends ASTNode
{
    public static final long serialVersionUID = 1L;
    public Find(ASTNode l, ASTNode r) {
		super(l,r);
	}
	
	public ASTNode copy()
	{
	    return new Find(getChild(0), getChild(1));
	}
	
	public boolean typecheck(SymbolTable st) {
	    // Avoid the undefined identifiers on the left. 
	    return getChild(1).typecheck(st);
	}
	
	public String toString() {
	    return "find "+getChild(0)+": "+getChild(1);
	}
}
