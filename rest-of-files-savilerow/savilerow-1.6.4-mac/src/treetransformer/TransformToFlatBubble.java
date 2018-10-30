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

// Do flattening on AuxBubble only. 

// What if a domain inside a bubble contains a bubble?

public class TransformToFlatBubble extends TreeTransformerBottomUp
{
    public TransformToFlatBubble(Model mod)
    {
        super(mod);
    }
    
	protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof AuxBubble && curnode.toFlatten(false) && (curnode.getParent()==null || !(curnode.getParent() instanceof ToVariable)))  // Needs flattening and not already flat.
	    {
	        ASTNode domain=curnode.getDomainForId(curnode.getChild(0));
	        ASTNode auxvar=m.global_symbols.newAuxiliaryVariable(domain);
	        
	        // Extract the constraints and sub in the new identifier 
            ASTNode old_id=curnode.getChild(0);
            ReplaceASTNode r1=new ReplaceASTNode(old_id, auxvar);
            ASTNode temp=r1.transform(curnode.getChild(2));
	        
	        m.global_symbols.auxVarRepresentsConstraint( auxvar.toString(), temp.toString());
            return new NodeReplacement(auxvar, null, temp);
	    }
	    
	    if(curnode instanceof Bubble) {
	        //  Simple case -- just lift the conjunction of constraints out of the bubble.
	        // Attaches constraints to nearest Bool context. If there is another bubble containing this one,
	        // the constraints in this one will remain within the outer bubble because bubbles always have
	        // a bool context.
	        return new NodeReplacement(curnode.getChild(0), curnode.getChild(1), null);
	    }
	    
	    return null;
	}
}
