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

// sum1 = sum2  ==>  sum1 - sum2 = 0
// This makes it possible to use the TransformEqualConst(Class) to avoid any aux variables.

public class TransformSumEqualSum extends TreeTransformerBottomUpNoWrapper
{
    public TransformSumEqualSum(Model mod) {
        super(mod);
    }
    
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof Equals)
        {
            ASTNode c1=curnode.getChild(0);
            ASTNode c2=curnode.getChild(1);
            if(c1 instanceof WeightedSum && c2 instanceof WeightedSum) {
                return new NodeReplacement(new Equals(BinOp.makeBinOp("-", c1, c2), new NumberConstant(0) ));
            }
        }
        return null;
    }
}
