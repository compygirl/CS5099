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

// Represents a Dim statement with one id, one domain (InfiniteIntegerDomain or BooleanDomain) and dimension.

public class Dim extends ASTNode
{
    public static final long serialVersionUID = 1L;
    public int dimensions;
    public Dim(ASTNode l, ASTNode d, int dim)
	{
		super(l,d);
		dimensions=dim;
	}
	public ASTNode copy()
	{
	    return new Dim(getChild(0), getChild(1), dimensions);
	}
	@Override
    public int hashCode() {
        return dimensions*17 + 13*getChild(0).hashCode() + 23*getChild(1).hashCode();
    }
}
