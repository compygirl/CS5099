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
import java.util.HashMap;

// Turn MatrixDeref containing decision-variable indices into an Element function.

public class TransformMatrixDeref extends TreeTransformerBottomUpNoWrapper
{
    public TransformMatrixDeref(Model _m) {
        super(_m);
    }
    
    protected NodeReplacement processNode(ASTNode curnode) {
	    if(curnode instanceof MatrixDeref || curnode instanceof SafeMatrixDeref)
        {
            // Find out if it needs to be translated to element (safe element)
            ArrayList<ASTNode> ch=curnode.getChildren();
            boolean hasVariableChildren=false;
            for(int i=1; i<ch.size(); i++)
            {
                // If it's a local variable, this probably can't tell, and therefore doesn't change it to element.
                // Need SymbolCell
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
	
	////  Somewhere in here it assumes that all matrices are indexed from 0!!! 
	
	
	protected ASTNode matrixDerefToElement(ASTNode matrixderef)
	{
		ASTNode matrix=matrixderef.getChild(0);
	    ArrayList<ASTNode> indices = matrixderef.getChildren(); indices.remove(0);
	    
	    ArrayList<ASTNode> matdimensions;
	    
	    //  Does not handle case where matrix is a slice, or a function returning a matrix. 
	    //  Therefore needs to be called after destroyMatrices and simplify.
	    
	    //  Also does not handle cases where matrix is a slice of a constant (given) matrix.
	    //  Therefore needs to be called after TransformSubInConstantMatrices.
	    
	    if(matrix instanceof Identifier) {
	        matdimensions=((MatrixDomain) m.global_symbols.getDomain(matrix.toString())).getMDIndexDomains();
	    }
	    else {
	        assert matrix instanceof CompoundMatrix || matrix instanceof EmptyMatrix;
	        matdimensions=matrix.getIndexDomains();
	    }
	    
	    ////////////////////////////////////////////////////////////////////////
	    // First identify the constant indices and use them to slice out
	    // a multi-dimensional slice of the matrix.
	    
	    ArrayList<ASTNode> constant_indices = new ArrayList<ASTNode>();
	    ArrayList<ASTNode> variable_indices = new ArrayList<ASTNode>();
	    ArrayList<Integer> variable_indices_indices = new ArrayList<Integer>();   // The indices of the variables in indices.
	    
	    for(int i=0; i<indices.size(); i++)
	    {
	        ASTNode e=indices.get(i);
	        if(e.isConstant())
	        {
	            long a=e.getValue();
	            constant_indices.add(new NumberConstant(a));
	            
	            if(! matdimensions.get(i).containsValue(a)) {
	                return new NumberConstant(0);   /// Why is this OK when it's not a safematrixderef?
	            }
	            
	            /*Intpair bndscheck=matdimensions.get(i).getBounds();  // get the relevant index domain
	            if(a<bndscheck.lower || a>bndscheck.upper) {
	                // Found a constant index that is out of bounds, bail out.
	                return new NumberConstant(0);
	            }*/
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
	    
	    // Can't assume it is indexed from 0.
	    // The matrix may come from a matrix literal or comprehension, which
	    // would not have been shifted to 0. 
	    
	    long[] matdimensions2=new long[matdimensions.size()];
	    
	    ArrayList<ArrayList<Long>> matindexsets=new ArrayList<ArrayList<Long>>();
	    
	    for(int i=0; i<matdimensions2.length; i++)
	    {
	        ArrayList<Long> valset=matdimensions.get(i).getValueSet();
	        matindexsets.add(valset);
	        //matdimensions2[i]=valset.get(valset.size()-1)-valset.get(0)+1;
	        matdimensions2[i]=valset.size();
	    }
	    
	    long totalArraySize=1;   // total number of elemtns in the array
	    
	    ArrayList<ASTNode> sumVars=new ArrayList<ASTNode>();
	    ArrayList<Long> sumWeights=new ArrayList<Long>();
	    
	    for(int j=0; j<variable_indices_indices.size(); j++)
	    {
	        int i=variable_indices_indices.get(j);
	        totalArraySize=totalArraySize*matdimensions2[i];
	        
	        // find product of rest of array dimensions from i+1 ..n
	        long prod=1;
	        for(int k=i+1; k<matdimensions2.length; k++)
	        {
	            if(!(constant_indices.get(k) instanceof NumberConstant)) {
	                prod=prod*matdimensions2[k];
	            }
	        }
	        sumWeights.add(prod);
	        
	        ASTNode idx=indices.get(i).copy();
	        
	        // Shift or map idx to be contiguous from 0.
	        
	        ArrayList<Long> indexset=matindexsets.get(i);
	        
	        if(indexset.get(indexset.size()-1)-indexset.get(0)+1 > indexset.size()) {
	            // Not contiguous, needs a Mapping.
                HashMap<Long,Long> mapping=new HashMap<Long,Long>();
                for(int k=0; k<indexset.size(); k++) {
                    mapping.put(indexset.get(k), (long)k);
                }
                
                idx=new Mapping(mapping, idx);
	        }
	        else if(indexset.get(0)!=0) {
	            idx=BinOp.makeBinOp("-", idx, new NumberConstant(indexset.get(0)));
	        }
	        
	        sumVars.add(idx);
	    }
	    
	    ASTNode index_expr=new WeightedSum(sumVars, sumWeights);
	    
		// Make the element constraint
		ASTNode element;
		if(CmdFlags.getGecodetrans() || CmdFlags.getMinizinctrans() ) {
		    index_expr=BinOp.makeBinOp("+", index_expr, new NumberConstant(1));
		    if(matrixderef instanceof SafeMatrixDeref) {
		        element= new SafeElementOne(new Flatten(matrixslice), index_expr);
		    }
		    else {
		        element= new ElementOne(new Flatten(matrixslice), index_expr);
		    }
		}
		else {
		    if(matrixderef instanceof SafeMatrixDeref) {
		        element=new SafeElement(new Flatten(matrixslice), index_expr);
		    }
		    else {
		        element=new Element(new Flatten(matrixslice), index_expr);
		    }
		}
		
		return element;
	}
}
