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

//  Special-case flattener for equality between two expressions that both need
// flattening. Makes one aux variable for both.

// Basically an instance-level transformation made to work in the class transformer
// Should be adapted to transform equality inside foralls etc.

public class TransformEqualClass extends TreeTransformerBottomUpNoWrapper
{
    public TransformEqualClass(Model mod) {
        super(mod);
    }
    
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof Equals  
	        &&  (curnode.getParent()==null || curnode.getParent().inTopConjunction()) )
        {
            System.out.println("Found candidate equality to pre-flatten:"+curnode);
            ASTNode c1=curnode.getChild(0);
            ASTNode c2=curnode.getChild(1);
            if(c1.toFlatten(false) && c2.toFlatten(false)
                && c1.getCategory()>=ASTNode.Decision && c2.getCategory()>=ASTNode.Decision) {
                // If in top-level conjunction or in foralls etc.
                PairASTNode a=c1.getBoundsAST();
                PairASTNode b=c2.getBoundsAST();
                
                // Intersection.
                a.e1=new Max(a.e1, b.e1);
                a.e2=new Min(a.e2, b.e2);
                TransformSimplify ts=new TransformSimplify();
                a.e1=ts.transform(a.e1);
                a.e2=ts.transform(a.e2);
                
                if(a.e1.isConstant() && a.e2.isConstant() && a.e1.getValue()>a.e2.getValue() ) {  // two expressions can't be equal.
                    return new NodeReplacement(new BooleanConstant(false));
                }
                
                if(a.e1.isConstant() && a.e2.isConstant() && a.e1.getValue()==a.e2.getValue() ) { // Both expressions equal a constant.
                    return new NodeReplacement(new And(new Equals(c1, new NumberConstant(a.e1.getValue() )), 
                        new Equals(c2, new NumberConstant(a.e2.getValue() ))));
                }
                
                // CUT'N'PASTE FROM TransformToFlatClass
                // Collect all quantifier id's in the exression, and their corresponding quantifier.
                ArrayList<ASTNode> q_id=new ArrayList<ASTNode>();         // Collect quantifier id's in curnode
                ArrayList<ASTNode> quantifiers=new ArrayList<ASTNode>();  // collect quantifiers.
                ArrayList<ASTNode> conditions=new ArrayList<ASTNode>();   // Collect parameter expressions
                
                TransformToFlatClass.findQuantifierIds(q_id, quantifiers, conditions, curnode);
                
                // Extract and process domains from the quantifiers
                ArrayList<ASTNode> qdoms=new ArrayList<ASTNode>();
                TransformToFlatClass.extractDomains(q_id, quantifiers, conditions, qdoms, curnode);
                
                // Save the list in the order they appear in the expression.
                ArrayList<ASTNode> q_id_ordered=new ArrayList<ASTNode>(q_id);
                ArrayList<ASTNode> qdoms_ordered=new ArrayList<ASTNode>(qdoms);
                
                TransformToFlatClass.sortQuantifiers(q_id, quantifiers, qdoms);
                // End CUT'N'PASTE
                
                ASTNode auxvar;
                
                if(q_id.size()>0) {
                    ASTNode auxvarmatrixid=m.global_symbols.newAuxiliaryVariableMatrix(a.e1, a.e2, q_id, qdoms, conditions);
                    auxvar=new MatrixDeref(auxvarmatrixid, q_id);
                    m.global_symbols.auxVarRepresentsConstraint( auxvarmatrixid.toString(), curnode.toString());
                }
                else {
                    // It's just a single aux variable.
                    auxvar=m.global_symbols.newAuxiliaryVariable(a.e1, a.e2);
                    m.global_symbols.auxVarRepresentsConstraint( auxvar.toString(), curnode.toString());
                }
                
                ASTNode flatcon=new And(new ToVariable(c1, auxvar), new ToVariable(c2, auxvar));
                
                return new NodeReplacement(flatcon);
            }
        }
        return null;
    }
}



