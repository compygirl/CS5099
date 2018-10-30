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
import savilerow.*;

import java.util.ArrayList;

//  Break up a weighted sum

public class TransformBreakupSum extends TreeTransformerBottomUpNoWrapper
{
    public TransformBreakupSum(Model _m) { super(_m); }
    
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof WeightedSum && curnode.numChildren()>2)
        {
            ASTNode a=curnode.getChild(0);
            long w=((WeightedSum)curnode).getWeight(0);
            
            for(int i=1; i<curnode.numChildren(); i++) {
                ArrayList<Long> wts=new ArrayList<Long>();
                wts.add(w);
                wts.add(((WeightedSum)curnode).getWeight(i));
                ArrayList<ASTNode> ch=new ArrayList<ASTNode>();
                ch.add(a);
                ch.add(curnode.getChild(i));
                
                a=new WeightedSum(ch, wts);
                
                w=1;
            }
            return new NodeReplacement(a);
        }
        
        return null;
    }
}

