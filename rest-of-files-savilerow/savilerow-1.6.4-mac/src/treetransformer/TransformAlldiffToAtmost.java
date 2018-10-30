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

import savilerow.*;
import savilerow.expression.*;
import savilerow.model.*;

import java.util.*;

// Decompose alldiff into a set of atmost constraints, one for each value. 
// Used for SAT encoding initially 

public class TransformAlldiffToAtmost extends TreeTransformerBottomUpNoWrapper
{
    public TransformAlldiffToAtmost(Model _m) { super(_m); }
    
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof AllDifferent && curnode.getChild(0) instanceof CompoundMatrix && curnode.getChild(0).numChildren()>3)
        {
            ArrayList<ASTNode> contents=curnode.getChild(0).getChildren();
            
            // Take the union of the intersection of the domain of each pair of variables
            // Other values (that appear in one variable) are not relevant.
            ASTNode values=new IntegerDomain(new EmptyRange());
            
            for(int i=1; i<contents.size(); i++) {
                ASTNode dom1;
                if(contents.get(i) instanceof Identifier) {
                    dom1=((Identifier)contents.get(i)).getDomain();
                }
                else if(contents.get(i).isConstant()) {
                    dom1=new IntegerDomain(new Range(contents.get(i), contents.get(i)));
                }
                else {
                    Intpair p1=contents.get(i).getBounds();
                    dom1=new IntegerDomain(new Range(new NumberConstant(p1.lower), new NumberConstant(p1.upper)));
                }
                
                for(int j=i+1; j<contents.size(); j++) {
                    ASTNode dom2;
                    
                    if(contents.get(j) instanceof Identifier) {
                        dom2=((Identifier)contents.get(j)).getDomain();
                    }
                    else if(contents.get(j).isConstant()) {
                        dom2=new IntegerDomain(new Range(contents.get(j), contents.get(j)));
                    }
                    else {
                        Intpair p1=contents.get(j).getBounds();
                        dom2=new IntegerDomain(new Range(new NumberConstant(p1.lower), new NumberConstant(p1.upper)));
                    }
                    
                    values=new Union(values, new Intersect(dom1, dom2));
                }
            }
            
            TransformSimplify ts=new TransformSimplify();
            values=ts.transform(values);
            
            ArrayList<Intpair> value_intervals=values.getIntervalSet();
            
            ArrayList<ASTNode> decomp=new ArrayList<ASTNode>();
            for(Intpair p : value_intervals) {
                for(long val=p.lower; val<=p.upper; val++) {
                    decomp.add(new AtMost(curnode.getChild(0), 
                        CompoundMatrix.makeCompoundMatrix(new NumberConstant(1)), 
                        CompoundMatrix.makeCompoundMatrix(new NumberConstant(val))));
                }
            }
            return new NodeReplacement(new And(decomp));
        }
        return null;
    }
    
}

