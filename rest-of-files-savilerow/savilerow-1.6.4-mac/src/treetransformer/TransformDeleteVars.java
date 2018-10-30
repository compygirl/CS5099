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
import savilerow.CmdFlags;

// This is an unusual one, it makes a list of other transforms to do after. 

public class TransformDeleteVars extends TreeTransformerBottomUpNoWrapper
{
    public TransformDeleteVars(Model _m) { 
        super(_m);
    }
    
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    // Take advantage of bottom-up rewriting -- do any replacements 
        // before checking for equality constraints. 
        if(curnode instanceof Identifier) {
            ASTNode replacement = m.global_symbols.replacements.get(curnode);
            if(replacement!=null) {
                return new NodeReplacement(replacement);
            }
        }
        // Now can assume that an equality will not contain an identifier that
        // has already been replaced. 
        
        return doDeleteVars(curnode, m);
    }
    
    public static NodeReplacement doDeleteVars(ASTNode curnode, Model m) {
        if(curnode.getParent()!=null && curnode.getParent().inTopAnd()) {
            
            // Only do assignment after aggregation has finished because it prevents aggregation of allDiff. 
            boolean doAssignVar=(!CmdFlags.getUseAggregate()) || CmdFlags.getAfterAggregate();
	        // Equality types.
	        if(curnode instanceof Equals || curnode instanceof Iff || curnode instanceof ToVariable) {
	            ASTNode c0=curnode.getChild(0);
	            ASTNode c1=curnode.getChild(1);
	            if(c0 instanceof Identifier && c1.isConstant() && doAssignVar) {
	                //  Makes a big assumption: that the equality would already have simplified to false 
	                //  if the value is not in domain. 
	                m.global_symbols.assignVariable(c0, c1);
                    return new NodeReplacement(new BooleanConstant(true));
                }
                if(c1 instanceof Identifier && c0.isConstant() && doAssignVar) {
                    //  Makes a big assumption: that the equality would already have simplified to false 
	                //  if the value is not in domain.
                    m.global_symbols.assignVariable(c1, c0);
                    return new NodeReplacement(new BooleanConstant(true));
                }
	            if(c0 instanceof Identifier && c1 instanceof Identifier
	                && !(c0.equals(c1))) {
	                // Last condition makes sure the two identifiers are not the same. This can occur 
	                // when there is a loop of equalities.
	                m.global_symbols.unifyVariables(c0, c1);
	                return new NodeReplacement(new BooleanConstant(true));
	            }
	            
	            // Cases where one variable is the negation of the other
	            if(CmdFlags.getMiniontrans()) {
                    if(c0 instanceof Identifier && c1 instanceof Negate && c1.getChild(0) instanceof Identifier) {
                        m.global_symbols.unifyVariablesNegated(c0, c1);
                        return new NodeReplacement(new BooleanConstant(true));
                    }
                    if(c1 instanceof Identifier && c0 instanceof Negate && c0.getChild(0) instanceof Identifier) {
                        m.global_symbols.unifyVariablesNegated(c1, c0);
                        return new NodeReplacement(new BooleanConstant(true));
                    }
                }
            }
            
            // Bare or negated boolean variable in the top-level And.
            if(curnode instanceof Identifier && doAssignVar) {
                m.global_symbols.assignVariable(curnode, new BooleanConstant(true));
                return new NodeReplacement(new BooleanConstant(true));
            }
            if(curnode instanceof Negate && curnode.getChild(0) instanceof Identifier && doAssignVar) {
                m.global_symbols.assignVariable(curnode.getChild(0), new BooleanConstant(false));
                return new NodeReplacement(new BooleanConstant(true));
            }
        }
        return null;
    }
    
    
}

