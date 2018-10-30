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

// Turn product into MultiplyMapper for
// Dominion.

public class TransformProductToMult extends TreeTransformerBottomUpNoWrapper
{
    public TransformProductToMult(Model mod) {
        super(mod);
    }
    
	protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof Times) {
	        ASTNode c1=curnode.getChild(0);
	        ASTNode c2=curnode.getChild(1);
	        
	        if(c1.getCategory()<=ASTNode.Quantifier && c2.getCategory()==ASTNode.Decision) {
	            return new NodeReplacement(new MultiplyMapper(c2, c1));
	        }
	        if(c2.getCategory()<=ASTNode.Quantifier && c1.getCategory()==ASTNode.Decision) {
	            return new NodeReplacement(new MultiplyMapper(c1, c2));
	        }
	    }
	    return null;
	}
}
