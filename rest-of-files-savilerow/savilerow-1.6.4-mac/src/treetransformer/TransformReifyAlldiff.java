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

//  Get rid of reify(alldiff()) because Gecode doesn't have it.

public class TransformReifyAlldiff extends TreeTransformerBottomUpNoWrapper
{
    public TransformReifyAlldiff(Model _m) { super(_m); }
    
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode.getParent() instanceof ToVariable && curnode instanceof AllDifferent)
        {
            if(curnode.getChild(0).numChildren()>2) {
                // reified alldiff
                // Replace with pairwise decomposition.
                ArrayList<ASTNode> conjunction=new ArrayList<ASTNode>();
                for(int i=0; i<curnode.getChild(0).numChildren(); i++) {
                    for(int j=i+1; j<curnode.getChild(0).numChildren(); j++) {
                        conjunction.add(new AllDifferent(curnode.getChild(0).getChild(i), curnode.getChild(0).getChild(j)));
                    }
                }
                
                return new NodeReplacement(new And(conjunction));
            }
        }
        return null;
    }
}

