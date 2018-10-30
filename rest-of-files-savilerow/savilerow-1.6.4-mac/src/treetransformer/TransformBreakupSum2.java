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

import java.util.*;

//  Break up a weighted sum -- this version takes the pair with the smallest intervals 
//  and replaces with an aux. 

public class TransformBreakupSum2 extends TreeTransformerBottomUp
{
    public TransformBreakupSum2(Model _m) { super(_m); }
    
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    // Sort for smallest interval first. 
        class cmpastnode implements Comparator<ASTNode> {
            public int compare(ASTNode x, ASTNode y) {
                Intpair p1=x.getBounds();
                long range_1=p1.upper-p1.lower+1;
                
                Intpair p2=y.getBounds();
                long range_2=p2.upper-p2.lower+1;
                
                if(range_1<range_2) {
                    return -1;
                }
                else if(range_1==range_2) {
                    return 0;
                }
                else {
                    return 1;
                }
            }
        }
        
	    if(curnode instanceof WeightedSum && curnode.numChildren()>2)
        {
            cmpastnode cmpast=new cmpastnode();
            
            PriorityQueue<ASTNode> p=new PriorityQueue<ASTNode>(curnode.numChildren(), cmpast);
            
            ArrayList<Long> wts=((WeightedSum)curnode).getWeights();
            
            for(int i=0; i<curnode.numChildren(); i++) {
                p.offer(new MultiplyMapper(curnode.getChild(i), new NumberConstant(wts.get(i))));
            }
            
            ArrayList<ASTNode> newcts = new ArrayList<ASTNode>();
            
            while(p.size()>1) {
                ASTNode m1=p.poll();
                ASTNode m2=p.poll();
                
                // Pull apart the MultiplyMappers and make a sum. 
                ArrayList<Long> newwts=new ArrayList<Long>();
                newwts.add(m1.getChild(1).getValue());
                newwts.add(m2.getChild(1).getValue());
                ArrayList<ASTNode> ch=new ArrayList<ASTNode>();
                ch.add(m1.getChild(0));
                ch.add(m2.getChild(0));
                
                ASTNode newsumct=new WeightedSum(ch, newwts);
                
                ASTNode auxvar=m.global_symbols.newAuxHelper(newsumct);
                ASTNode flatcon=new ToVariable(newsumct, auxvar);
                m.global_symbols.auxVarRepresentsConstraint(auxvar.toString(), newsumct.toString());
                
                newcts.add(flatcon);
                p.offer(new MultiplyMapper(auxvar, new NumberConstant(1)));
            }
            
            ASTNode mm=p.poll();
            assert mm.getChild(1).equals(new NumberConstant(1));
            
            return new NodeReplacement(mm.getChild(0), null, new And(newcts));
        }
        
        return null;
    }
}

