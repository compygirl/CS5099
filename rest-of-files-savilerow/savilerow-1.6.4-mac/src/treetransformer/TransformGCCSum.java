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

//  Add an implied sum constraint to a GCC constraint's cardinality variables.  

public class TransformGCCSum extends TreeTransformerBottomUpNoWrapper
{
    public TransformGCCSum(Model _m) { super(_m); }
    
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof GlobalCard && !(curnode.getParent() instanceof Tag) 
	        && curnode.getChild(0) instanceof CompoundMatrix 
	        && curnode.getChild(1) instanceof CompoundMatrix 
	        && curnode.getChild(2) instanceof CompoundMatrix)
        {
            ArrayList<ASTNode> target=curnode.getChild(0).getChildren();
            target.remove(0);  // throw away index domain. 
            ArrayList<ASTNode> vals=curnode.getChild(1).getChildren();
            vals.remove(0);
            ArrayList<ASTNode> occs=curnode.getChild(2).getChildren();
            occs.remove(0);
            
            if(curnode.getChild(1).getCategory() == ASTNode.Constant) {
                ////////////////////////////////////////////////////////////////////////////////
                //  
                //   Constraint on the cardinality variables.
                
                //   If all values of target variables are in vals, then sum 
                //   cardinalities == | target vars |.
                
                ArrayList<Long> vals_long=new ArrayList<Long>();
                for(int i=0; i<occs.size(); i++) {
                    vals_long.add(vals.get(i).getValue());
                }
                
                HashSet<Long> vals_set=new HashSet<Long>(vals_long);
                
                // Check all values of target variables.
                boolean complete=true;
                
                outerloop:
                for(int i=0; i<target.size(); i++) {
                    ArrayList<Intpair> targetdom;
                    if(target.get(i) instanceof Identifier) {
                        targetdom=m.global_symbols.getDomain(target.get(i).toString()).getIntervalSet();
                    }
                    else {
                        targetdom=new ArrayList<Intpair>();
                        targetdom.add(target.get(i).getBounds());
                    }
                    
                    for(int j=0; j<targetdom.size(); j++) {
                        for(long k=targetdom.get(j).lower; k<=targetdom.get(j).upper; k++) {
                            if(!vals_set.contains(k)) {
                                complete=false;
                                break outerloop; 
                            }
                        }
                    }
                }
                
                ASTNode newcon;
                if(complete) {
                    newcon=new Equals(new NumberConstant(target.size()), new WeightedSum(occs));
                }
                else {
                    // Can only say that the sum of the card vars is <= number of target variables. 
                    newcon=new LessEqual(new WeightedSum(occs), new NumberConstant(target.size()));
                }
                
                return new NodeReplacement(new And(new Tag(curnode, true), newcon));
            }
        }
        return null;
    }
}

