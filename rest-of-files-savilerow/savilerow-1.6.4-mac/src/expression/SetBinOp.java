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
import savilerow.*;

/* =============================================================================
 Base class for the set binary operators e.g. Intersect
 Just exists tfo replace typecheck.
==============================================================================*/

abstract public class SetBinOp extends BinOp
{
    public SetBinOp(ASTNode l, ASTNode r)
	{
		super(l,r);
	}
	
	public boolean typecheck(SymbolTable st) {
	    for(int i=0; i<2; i++) {
	        if(!getChild(i).typecheck(st)) return false;
	        if(!getChild(i).isSet()) {
	            CmdFlags.println("ERROR: Unexpected non-set in set operator: "+this);
	            return false;
	        }
        }
        return true;
    }
}