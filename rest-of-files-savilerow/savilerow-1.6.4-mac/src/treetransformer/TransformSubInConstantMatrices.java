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

//  Traverse the constraints, in each case where a constant matrix is referred to,
//  substitute in that matrix 

public class TransformSubInConstantMatrices extends TreeTransformerBottomUpNoWrapper
{
    public TransformSubInConstantMatrices(Model _m) { super(_m); }
    
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof Identifier 
	        && ( !(curnode.getParent() instanceof Table || curnode.getParent() instanceof NegativeTable) || curnode.getChildNo()!=1 )   // It is not the second child of a table constraint (i.e. the table itself).
	        && m.global_symbols.getCategory(curnode.toString())==ASTNode.Constant ) // It is a constant matrix. 
        {
            return new NodeReplacement(m.global_symbols.getConstantMatrix(curnode.toString()));
        }
        return null;
    }
}

