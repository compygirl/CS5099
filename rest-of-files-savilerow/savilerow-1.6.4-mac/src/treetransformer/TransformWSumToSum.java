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

// Get rid of all mappers

public class TransformWSumToSum extends TreeTransformerBottomUpNoWrapper
{
    public TransformWSumToSum(Model mod) {
        super(mod);
    }
    
	protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof WeightedSum) {
	        if(((WeightedSum)curnode).isAll1Weights()) return null;
	        
	        ArrayList<Long> w=((WeightedSum)curnode).getWeights();
	        ArrayList<ASTNode> ch=curnode.getChildren();
	        
	        for(int i=0; i<ch.size(); i++) {
	            if( w.get(i) != 1) {
	                ch.set(i, new Times(ch.get(i), new NumberConstant(w.get(i))));
	                w.set(i, 1L);
	            }
	        }
	        
	        return new NodeReplacement(new WeightedSum(ch, w));
	    }
	    return null;
	}
}
