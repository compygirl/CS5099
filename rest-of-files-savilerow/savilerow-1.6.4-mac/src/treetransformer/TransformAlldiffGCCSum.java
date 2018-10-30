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

//  Add an implied sum constraint(s) to an alldifferent. 

public class TransformAlldiffGCCSum extends TreeTransformerBottomUpNoWrapper
{
    public TransformAlldiffGCCSum(Model _m) { super(_m); }
    
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof AllDifferent && curnode.getChild(0) instanceof CompoundMatrix && !(curnode.getParent() instanceof Tag))
        {
            ArrayList<ASTNode> ch=curnode.getChild(0).getChildren();
            ch.remove(0);  // throw away index domain. 
            
            if(ch.size()<=2) {
                // Actually a not-equal constraint. Don't add the implied sum. 
                return null;
            }
            
            // Upper bound sum. 
            ArrayList<Long> ub=new ArrayList<Long>();
            ArrayList<Long> lb=new ArrayList<Long>();
            
            for(int i=0; i<ch.size(); i++) {
                Intpair a=ch.get(i).getBounds();
                ub.add(a.upper);
                lb.add(a.lower);
            }
            
            // Upper bound sum. 
            Collections.sort(ub);
            Collections.sort(lb);
            
            // 'Enforce' alldiff by not allowing equal values, make it always increasing (lower bounds) 
            for(int i=0; i<lb.size()-1; i++) {
                if(lb.get(i)>=lb.get(i+1)) lb.set(i+1, lb.get(i)+1);
            }
            
            // Same for upper bounds. Traverse list backwards and make it always decreasing. 
            for(int i=ub.size()-1; i>0; i--) {
                if(ub.get(i)<=ub.get(i-1)) ub.set(i-1, ub.get(i)-1);
            }
            
            // Just sum the lists. 
            long upperbound=0, lowerbound=0;
            for(int i=0; i<lb.size(); i++) {
                upperbound=upperbound+ub.get(i);
                lowerbound=lowerbound+lb.get(i);
            }
            //System.out.println("lowerbound: "+lowerbound+" upperbound: "+upperbound+" lblist:"+lb+ " ublist:"+ub);
            ASTNode newcon;
            if(lowerbound>upperbound) {
                return new NodeReplacement(new BooleanConstant(false));
            }
            if(lowerbound==upperbound) {
                newcon=new Equals(new NumberConstant(lowerbound), new WeightedSum(ch));
            }
            else {
                newcon=new And(
                    new LessEqual(new NumberConstant(lowerbound), new WeightedSum(ch)), 
                    new LessEqual(new WeightedSum(ch), new NumberConstant(upperbound)));
            }
            //System.out.println("Adding new constraint:"+newcon);
            
            return new NodeReplacement(new And(new Tag(curnode, true), newcon));
        }
        
        //   GCC sum derived in a very similar way as AllDifferent.
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
                // Unless we know the values we are dealing with, we can't generate the sum on 
                // the target variables. 
                
                // Get the max cardinality of each value. This will be used as an upper
                // bound on the number of occurrences of each value in the ub and lb lists. 
                
                ArrayList<Long> maxcard=new ArrayList<Long>();
                ArrayList<Long> vals_long=new ArrayList<Long>();
                for(int i=0; i<occs.size(); i++) {
                    maxcard.add(occs.get(i).getBounds().upper);
                    vals_long.add(vals.get(i).getValue());
                }
                
                // Upper bound sum. 
                ArrayList<Long> ub=new ArrayList<Long>();
                ArrayList<Long> lb=new ArrayList<Long>();
                
                for(int i=0; i<target.size(); i++) {
                    Intpair a=target.get(i).getBounds();
                    ub.add(a.upper);
                    lb.add(a.lower);
                }
                
                // Upper bound sum. 
                Collections.sort(ub);
                Collections.sort(lb);
                
                // 'Enforce' GCC by not allowing equal values in some cases, make it increasing (lower bounds) 
                for(int i=0; i<lb.size(); i++) {
                    
                    // Make it non-decreasing first. 
                    if(i>0 && lb.get(i-1)>lb.get(i)) lb.set(i, lb.get(i-1));
                    
                    int value_index=vals_long.indexOf(lb.get(i));
                    
                    if(value_index>-1) {
                        // There is a constraint on this value. 
                        // Count back down the array for the number of occurrences of the current value (occs_val) 
                        // From i backwards.
                        
                        int occs_val=0;
                        for(int j=i; j>=0 && lb.get(j)==lb.get(i); j--) occs_val++;
                        
                        if(occs_val>maxcard.get(value_index)) {
                            assert occs_val-maxcard.get(value_index)==1;
                            
                            lb.set(i, lb.get(i)+1);  // Move on to the next value. 
                            
                            // Now need to check index i again because the value has changed.
                            i--;
                        }
                        
                    }
                }
                
                // 'Enforce' GCC by not allowing equal values in some cases, make it decreasing (upper bounds) 
                for(int i=ub.size()-1; i>=0; i--) {
                    // Make it non-increasing first. 
                    if( i+1 < ub.size() && ub.get(i)>ub.get(i+1)) ub.set(i, ub.get(i+1));
                    
                    int value_index=vals_long.indexOf(ub.get(i));
                    
                    if(value_index>-1) {
                        // There is a constraint on this value. 
                        // Count back down the array for the number of occurrences of the current value (occs_val). 
                        // From i forwards. 
                        
                        int occs_val=0;
                        for(int j=i; j<ub.size() && ub.get(j)==ub.get(i); j++) occs_val++;
                        
                        if(occs_val>maxcard.get(value_index)) {
                            assert occs_val-maxcard.get(value_index)==1;
                            
                            ub.set(i, ub.get(i)-1);  // Move on to the next value.
                            
                            // Now need to check index i again because the value has changed.
                            i++;
                        }
                        
                    }
                }
                
                
                // Just sum the lists. 
                long upperbound=0, lowerbound=0;
                for(int i=0; i<lb.size(); i++) {
                    upperbound=upperbound+ub.get(i);
                    lowerbound=lowerbound+lb.get(i);
                }
                //System.out.println("lowerbound: "+lowerbound+" upperbound: "+upperbound+" lblist:"+lb+ " ublist:"+ub);
                ASTNode newcon;
                if(lowerbound>upperbound) {
                    return new NodeReplacement(new BooleanConstant(false));
                }
                if(lowerbound==upperbound) {
                    newcon=new Equals(new NumberConstant(lowerbound), new WeightedSum(target));
                }
                else {
                    newcon=new And(
                        new LessEqual(new NumberConstant(lowerbound), new WeightedSum(target)), 
                        new LessEqual(new WeightedSum(target), new NumberConstant(upperbound)));
                }
                return new NodeReplacement(new And(new Tag(curnode, true), newcon));
            }
        }
        return null;
    }
}

