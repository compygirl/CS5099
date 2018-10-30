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


import savilerow.model.SymbolTable;
import savilerow.*;

//  toInt(...) takes a Boolean expression and converts the type to integer.
//  This is not required in any situation, it only exists to make E' similar to Essence.

public class ToInt extends Unary
{
    public static final long serialVersionUID = 1L;
    public ToInt(ASTNode a) {
        super(a);
    }
    
	public ASTNode copy() {
	    return new ToInt(getChild(0));
	}
	
	public ASTNode simplify() {
	    return getChild(0);
	}
	
	@Override
	public boolean typecheck(SymbolTable st) {
	    if(!getChild(0).typecheck(st)) return false;
	    
	    if(!getChild(0).isRelation()) {
	        CmdFlags.println("ERROR: Non-boolean expression in toInt function: "+this);
            return false;
	    }
        return true;
    }
	
	public boolean toFlatten(boolean propagate) { return false;}
	public boolean isNumerical() {
        return true;
    }
    
	public String toString()
	{
	    return "toInt("+getChild(0)+")";
	}
	
	public Intpair getBounds() {
	    return getChild(0).getBounds();
	}
	public PairASTNode getBoundsAST()  	{
	    return getChild(0).getBoundsAST();
	}
}
