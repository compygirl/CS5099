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

public class NegInfinity extends ASTNode
{
    public static final long serialVersionUID = 1L;
	public NegInfinity()  {
	    super();
	}
	public ASTNode copy() {
	    return new NegInfinity();
	}
	
	@Override
	public boolean equals(Object b)
	{
	    if(!(b instanceof NegInfinity))
	        return false;
	    return true;
	}
	@Override
    public int hashCode() {
        return 12443492;
    }
	
	public String toString()
	{
	    return "(-infty)";
	}
	
	public Intpair getBounds() {
	    return new Intpair(Long.MIN_VALUE, Long.MIN_VALUE);
	}
	public PairASTNode getBoundsAST() {
	    return new PairASTNode(this,this);
	}
	
	// Claim to be a constant and return the smallest long value. 
	public boolean isConstant() {
	    return true;
	}
	public long getValue() {
	    return Long.MIN_VALUE;
	}
	
	public boolean isNumerical() {
        return true;
    }
}