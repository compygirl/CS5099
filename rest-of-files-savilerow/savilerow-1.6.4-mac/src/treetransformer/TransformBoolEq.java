package savilerow.treetransformer;
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


import savilerow.expression.*;
import savilerow.model.*;

public class TransformBoolEq extends TreeTransformerBottomUpNoWrapper
{
    public TransformBoolEq(Model _m) { super(_m); }
    
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof Equals) {
	        ASTNode a=curnode.getChild(0);
	        ASTNode b=curnode.getChild(1);
	        if(b instanceof Identifier) {  // swap.
	            a=b;
	            b=curnode.getChild(0);
	        }
	        
	        if(a instanceof Identifier && a.isRelation() && b.isConstant() ) {
	            long bval=b.getValue();
	            if(bval==0) {
	                return new NodeReplacement(new Negate(a));
	            }
	            else if(bval==1) {
	                return new NodeReplacement(a.copy());
	            }
	            else {
	                // What's going on here -- equal simplify not working?
	                assert false;
	            }
	        }
	    }
	    return null;
    }
}

