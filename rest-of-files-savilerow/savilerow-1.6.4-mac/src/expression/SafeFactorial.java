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
import savilerow.model.SymbolTable;
import java.lang.Math;

public class SafeFactorial extends ASTNode
{
    public static final long serialVersionUID = 1L;
	public SafeFactorial(ASTNode a)
	{
		super(a);
	}
	
	public ASTNode copy() {
	    return new SafeFactorial(getChild(0));
	}
	
	public boolean toFlatten(boolean propagate) { return false; }
	public boolean isNumerical() {
        return true;
    }
    
    @Override
    public boolean typecheck(SymbolTable st) {
        if(!getChild(0).typecheck(st))
	        return false;
        
        if(getChild(0).getCategory() >= ASTNode.Decision) {
            System.out.println("ERROR: Factorial may not contain decision variables:"+this);
            return false;
        }
        
        if(getChild(0).getDimension()>0) {
            System.out.println("ERROR: Factorial may not contain a matrix:"+this);
            return false;
        }
        
        return true;
    }
    
	public ASTNode simplify()	{
	    if(getChild(0).isConstant()) {
	        long value=getChild(0).getValue();
	        
	        if(value<0) return new NumberConstant(1); // default value for negative numebrs.
	        
	        if(value>20) {
	            System.out.println("ERROR: Cannot evaluate factorial of number greater than 20 (because of overflow of 64-bit integer):"+this);
	            CmdFlags.exit();
	        }
	        
	        return new NumberConstant(fact(value));
	    }
	    return this;
	}
	
	private long fact(long value) {
	    long fact=1;
        
        for(long i=2; i<=value; i++) {
            fact=fact*i;
        }
        return fact;
	}
	
	public Intpair getBounds() {
	    Intpair a=getChild(0).getBounds();
	    long lower=0;  // Default value.
	    long upper=fact(20);
	    if(a.upper<=20) {
	        if(a.upper<0) {
	            upper=0;
	        }
	        else {
	            upper=fact(a.upper);
	        }
	    }
	    
	    a.lower=lower; a.upper=upper;  // Recycle object.
	    return a;
	}
	public PairASTNode getBoundsAST() {
	    PairASTNode a=getChild(0).getBoundsAST();
	    
	    a.e1=new SafeFactorial(a.e1);
	    a.e2=new SafeFactorial(a.e2);
	    return a;
	}
	
	// Loses its safeness.
	public String toString() {
	    return "factorial("+getChild(0)+")";
	}
}
