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
//  Transform a sum <  into sum <= ahead of Minion output as a sumleq constraint
//  Similarly turn X < sum  into X+1 <= sum

public class TransformSumLess extends TreeTransformerBottomUpNoWrapper
{
    public TransformSumLess() { super(null); }
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof Less)
        {
            // Checking that the sum has decision variables is for class-level mode:
            // if the sum is a parameter/quantifier expression, don't do this rearrangement.
            if(curnode.getChild(0) instanceof WeightedSum && curnode.getChild(0).getCategory()>ASTNode.Quantifier )
            {
                assert curnode.getChild(0).getCategory()==ASTNode.Decision;
                // Add 1 to the sum.
                ArrayList<ASTNode> l=new ArrayList<ASTNode>();
                l.add(curnode.getChild(0)); l.add(new NumberConstant(1));
                ASTNode a=new WeightedSum(l);
                return new NodeReplacement(new LessEqual(a, curnode.getChild(1)));
            }
            else if(curnode.getChild(1) instanceof WeightedSum && curnode.getChild(1).getCategory()>ASTNode.Quantifier )
            {
                assert curnode.getChild(1).getCategory()==ASTNode.Decision;
                // Subtract 1 from the sum.
                ArrayList<ASTNode> l=new ArrayList<ASTNode>();
                l.add(curnode.getChild(1)); l.add(new NumberConstant(-1));
                ASTNode a=new WeightedSum(l);
                return new NodeReplacement(new LessEqual(curnode.getChild(0), a));
            }
        }
        return null;
    }
}

