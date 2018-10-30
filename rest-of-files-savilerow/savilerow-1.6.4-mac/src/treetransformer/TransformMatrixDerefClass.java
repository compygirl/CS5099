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


import savilerow.CmdFlags;
import savilerow.expression.*;
import savilerow.model.*;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;

public class TransformMatrixDerefClass extends TreeTransformerBottomUpNoWrapper
{
    public TransformMatrixDerefClass(Model _m) {
        super(_m);
    }
    
    protected NodeReplacement processNode(ASTNode curnode) {
	    if(curnode instanceof MatrixDeref || curnode instanceof SafeMatrixDeref)
        {
            // Find out if it needs to be translated to element
            ArrayList<ASTNode> ch=curnode.getChildren();
            boolean hasVariableChildren=false;
            for(int i=1; i<ch.size(); i++)
            {
                if(ch.get(i).getCategory()==ASTNode.Decision)
                {
                    hasVariableChildren=true; break;
                }
            }
            
            // If it needs to be translated...
            if(hasVariableChildren)
            {
                ASTNode el=matrixDerefToElement(curnode);
                // Put the non-flat Element expression in place of the MatrixDeref
                return new NodeReplacement(el);
            }
        }
        return null;
	}
    
	// Returns the element expression.
	// No need to make the bounding constraints now because that is done by
	// TransformMakeSafe.
	
	protected ASTNode matrixDerefToElement(ASTNode arrayVariable)
	{
	    ASTNode matrix=arrayVariable.getChild(0);
	    ArrayList<ASTNode> indices = arrayVariable.getChildren(); indices.remove(0);
	    
	    ////////////////////////////////////////////////////////////////////////
	    // First identify the constant indices and use them to slice out
	    // a multi-dimensional slice of the array.
	    
	    ArrayList<ASTNode> constant_indices = new ArrayList<ASTNode>();
	    ArrayList<ASTNode> variable_indices = new ArrayList<ASTNode>();
	    ArrayList<Integer> variable_indices_indices = new ArrayList<Integer>();   // The indices of the variables in indices.
	    
	    for(int i=0; i<indices.size(); i++)
	    {
	        ASTNode e=indices.get(i);
	        
	        if(e.getCategory()<ASTNode.Decision)   // It will be constant at instance level.
	        {
	            constant_indices.add(e);
	        }
	        else
	        {
	            // Also need to put a .. into constant indices
	            constant_indices.add(new IntegerDomain(new Range(null, null)));
	            variable_indices.add(e);
	            variable_indices_indices.add(i);
	        }
	    }
	    
	    ASTNode matrixslice=new MatrixSlice(matrix, constant_indices, m.global_symbols);
	    
	    MatrixDomain matrixdom=((MatrixDomain) m.global_symbols.getDomain(matrix.toString()));
	    
	    ArrayList<ASTNode> arraydimensions=matrixdom.getMDIndexDomains();
	    
	    // Indexed from 0. This is symbolic, as opposed to TransformMatrixDeref which has integers.
	    
	    ASTNode[] arraydimensions2=new ASTNode[arraydimensions.size()];
	    
	    for(int i=0; i<arraydimensions2.length; i++) {
	        PairASTNode bnds=arraydimensions.get(i).getBoundsAST();
	        arraydimensions2[i]=BinOp.makeBinOp("+", BinOp.makeBinOp("-", bnds.e2, bnds.e1), new NumberConstant(1));
	    }
	    
	    ArrayList<ASTNode> sumVars=new ArrayList<ASTNode>();
	    ArrayList<ASTNode> sumWeights=new ArrayList<ASTNode>();
	    
	    for(int j=0; j<variable_indices_indices.size(); j++)
	    {
	        int i=variable_indices_indices.get(j);
	        
	        // find product of rest of array dimensions from i+1 ..n
	        ASTNode prod=new NumberConstant(1);
	        for(int k=i+1; k<arraydimensions2.length; k++)
	        {
	            if(constant_indices.get(k).isSet() && !(constant_indices.get(k).isFiniteSet())) {
	                prod=BinOp.makeBinOp("*", prod, arraydimensions2[k]);
	            }
	        }
	        
	        sumVars.add(indices.get(i));
	        sumWeights.add(prod);
	    }
	    
	    for(int j=0; j<sumVars.size(); j++) {
	        sumVars.set(j, BinOp.makeBinOp("*", sumWeights.get(j), sumVars.get(j))); 
	    }
	    
	    ASTNode index_expr=new WeightedSum(sumVars);
	    
		// Make the element function
		ASTNode element;
		if(arrayVariable instanceof MatrixDeref) {
		    element= new Element(new Flatten(matrixslice), index_expr);
		}
		else {
		    element= new SafeElement(new Flatten(matrixslice), index_expr);
		}
		
		return element;
	}
}
