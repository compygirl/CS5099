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

import java.util.ArrayList;
import java.util.HashMap;

//
//  Transform sum1  <= sum2  into sum1-sum2 <= 0
// so that it can be output as one sumleq ct.

public class TransformSumLeq extends TreeTransformerBottomUpNoWrapper
{
    public TransformSumLeq() { super(null); }
    
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof LessEqual)
        {
            if(curnode.getChild(0) instanceof WeightedSum && curnode.getChild(1) instanceof WeightedSum)
            {
                ArrayList<ASTNode> l=new ArrayList<ASTNode>();
                l.add(curnode.getChild(0)); l.add(curnode.getChild(1));
                ArrayList<Long> weights=new ArrayList<Long>();
                weights.add(1L); weights.add(-1L);
                ASTNode a=new WeightedSum(l, weights);
                return new NodeReplacement(new LessEqual(a, new NumberConstant(0)));
            }
        }
        return null;
    }
}

