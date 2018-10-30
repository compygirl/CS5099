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

import java.util.*;

// Push And through Forall. For Dominion output.

public class TransformForallAndToAndForall extends TreeTransformerBottomUpNoWrapper
{
    public TransformForallAndToAndForall() {
        super(null);
    }
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    if( curnode instanceof ForallExpression && curnode.getChild(2) instanceof And ) {
	        // Split the and.
	        ArrayList<ASTNode> newand=new ArrayList<ASTNode>();
	        for(int i=0; i<curnode.getChild(2).numChildren(); i++) {
	            newand.add(new ForallExpression(curnode.getChild(0), 
	                curnode.getChild(1), curnode.getChild(2).getChild(i)));
	        }
	        return new NodeReplacement(new And(newand));
	    }
	    return null;
	}
}
