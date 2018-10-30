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
import java.math.*;

// NaN

public class NoValue extends ASTNode
{
    public static final long serialVersionUID = 1L;
	public NoValue() {
		super();
	}
	
	public ASTNode copy()
	{
	    return new NoValue();
	}
	@Override
	public boolean equals(Object b)
	{
	    if(!(b instanceof NoValue))
	        return false;
	    return true;
	}
	@Override
    public int hashCode() {
        return 0xdeadbeef;
    }
	
	public String toString()
	{
	    return "Null";
	}
	
	public boolean isConstant() {
	    return true;
	}
	public boolean isNumerical() {
        return true;
    }
}