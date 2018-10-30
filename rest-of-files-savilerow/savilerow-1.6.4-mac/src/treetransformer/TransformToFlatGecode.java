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

// After ToFlat, model may still contain nested ToVariable expressions.
// This class removes them. 
// e.g. with constraint C,   (C <-tv> x) <-tv> y  becomes
// C <-tv> aux   /\   (aux <-tv> x) <-tv> y
//  which then simplifies to:
//  C <-tv> aux  /\  (aux <-> x) <-tv> y

public class TransformToFlatGecode extends TreeTransformerBottomUp
{
    public TransformToFlatGecode(Model m) {
        super(m);
    }
    
	protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode.toFlatten(false) && curnode.isRelation() && curnode.getParent()!=null && (curnode.getParent() instanceof ToVariable) 
	        && curnode.getParent().getParent()!=null && (curnode.getParent().getParent() instanceof ToVariable))  // Nested ToVariable.
	    {
	        ASTNode auxvar=m.global_symbols.newAuxHelper(curnode);
	        ASTNode flatcon=new ToVariable(curnode, auxvar);
            m.global_symbols.auxVarRepresentsConstraint(auxvar.toString(), curnode.toString());
            return new NodeReplacement(auxvar, null, flatcon);
	    }
	    return null;
	}
}
