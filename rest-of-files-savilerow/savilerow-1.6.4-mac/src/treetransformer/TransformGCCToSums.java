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
//  Transform GCC into sums/equals 

public class TransformGCCToSums extends TreeTransformerBottomUpNoWrapper
{
    public TransformGCCToSums() {
        super(null);
    }
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof GlobalCard)
        {
            assert curnode.getChild(0) instanceof CompoundMatrix;
            assert curnode.getChild(1) instanceof CompoundMatrix;   // Values
            assert curnode.getChild(2) instanceof CompoundMatrix;   // Cardinality variables/constants
            
            ArrayList<ASTNode> sums=new ArrayList<ASTNode>();
            
            for(int i=1; i<curnode.getChild(1).numChildren(); i++) {
                ASTNode value=curnode.getChild(1).getChild(i);
                
                ArrayList<ASTNode> newsum=new ArrayList<ASTNode>();
                
                for(int j=1; j<curnode.getChild(0).numChildren(); j++) {
                    newsum.add(new Equals(curnode.getChild(0).getChild(j), value));
                }
                
                ASTNode sum=new WeightedSum(newsum);
                
                sums.add(new Equals(sum, curnode.getChild(2).getChild(i)));
            }
            return new NodeReplacement(new And(sums));
            
        }
        return null;
    }
}

