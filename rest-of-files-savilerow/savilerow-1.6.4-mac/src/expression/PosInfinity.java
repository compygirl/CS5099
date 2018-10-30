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

public class PosInfinity extends ASTNode
{
    public static final long serialVersionUID = 1L;
	public PosInfinity()  {
	    super();
	}
	public ASTNode copy() {
	    return new PosInfinity();
	}
	
	@Override
	public boolean equals(Object b)
	{
	    if(!(b instanceof PosInfinity))
	        return false;
	    return true;
	}
	@Override
    public int hashCode() {
        return 76231894;
    }
	
	public String toString()
	{
	    return "(+infty)";
	}
	
	public Intpair getBounds() {
	    return new Intpair(Long.MAX_VALUE, Long.MAX_VALUE);
	}
	public PairASTNode getBoundsAST() {
	    return new PairASTNode(this,this);
	}
	
	// Claim to be a constant and return the largest long value. 
	public boolean isConstant() {
	    return true;
	}
	public long getValue() {
	    return Long.MAX_VALUE;
	}
	
	public boolean isNumerical() {
        return true;
    }
}