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

//  Rewrite ToVar(Element, Var) into logic and equality.

public class TransformElementForSAT extends TreeTransformerBottomUpNoWrapper
{
    public TransformElementForSAT(Model _m) { super(_m); }
    
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    // Decompose (Safe) Element. Becomes a disjunction.
	    
        if(curnode instanceof ToVariable && (curnode.getChild(0) instanceof SafeElement || curnode.getChild(0) instanceof Element)) {
            ASTNode idx=curnode.getChild(0).getChild(1);
            ASTNode result=curnode.getChild(1);
            ASTNode mat=curnode.getChild(0).getChild(0);
            
            // Can assume index is a decision variable, and get its domain. 
            ArrayList<Intpair> domainOfIndex = m.global_symbols.getDomain(idx.toString()).getIntervalSet();
            
            ArrayList<ASTNode> or=new ArrayList<ASTNode>();
            
            for (Intpair indexPair : domainOfIndex) {
                for (long i = indexPair.lower; i <= indexPair.upper; i++) {
                    ASTNode indexEqualConstant = new Equals(idx, new NumberConstant(i));
                    
                    ArrayList<ASTNode> andChildren = new ArrayList<ASTNode>();
                    andChildren.add(indexEqualConstant);
                    
                    ASTNode auxEquals;
                    if (i+1 < 1 || i+1 >= mat.numChildren()) {
                        // If out of bounds.
                        auxEquals = new Equals(result, new NumberConstant(0));
                    }
                    else {
                        auxEquals = new Equals(result, mat.getChild((int) (i+1)));
                    }
                    
                    andChildren.add(auxEquals);
                    
                    And and = new And(andChildren);
                    
                    or.add(and);
                }
            }
            return new NodeReplacement(new Or(or));
        }
        
        return null;
    }
}

