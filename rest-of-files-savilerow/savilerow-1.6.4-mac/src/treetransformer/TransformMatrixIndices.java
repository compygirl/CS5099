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

import java.util.*;

// The Mapping function introduced here should include entries for all values in the original variable... mapping out-of-bounds entries to a default value. 

public class TransformMatrixIndices extends TreeTransformerBottomUpNoWrapper
{
    // For a particular matrix, normalise the bounds so that they
    // go from 0..n or 1..n
    // Or replace a holey domain with one that is contiguous.
    // In the constructor, replace the domain of the matrix.
    // In processNode, replace any MatrixDeref or MatrixSlice objects with 
    // adjusted ones.
    
    private int new_lb;
    
    ArrayList<Long> offsets;   // list of offsets to apply.
    ArrayList<HashMap<Long,Long>> maps;    /// List of maps. Each dimension has either a map or an offset. 
    
    String matrixname;
    
    public TransformMatrixIndices(int new_lower_bound, Model mod, String mn)
    {
        super(mod); 
        new_lb=new_lower_bound;
        offsets=new ArrayList<Long>();
        maps=new ArrayList<HashMap<Long,Long>>();
        matrixname=mn;
        
        assert m.global_symbols.getDomain(matrixname) instanceof MatrixDomain;
        MatrixDomain md=(MatrixDomain)m.global_symbols.getDomain(matrixname);
        
        ArrayList<ASTNode> indexdoms=md.getMDIndexDomains();
        ArrayList<ASTNode> newidxdoms=new ArrayList<ASTNode>();
        for(ASTNode indexdom : indexdoms)
        {
            TransformSimplify ts=new TransformSimplify();
            ASTNode indexdomsimple=ts.transform(indexdom);
            
            ArrayList<Long> idxvals=indexdomsimple.getValueSet();
            if(idxvals.size()==0) {
                // Empty index domain. No shift. 
                offsets.add(0L);
                maps.add(null);
                newidxdoms.add(new IntegerDomain(new EmptyRange()));
            }
            else if(idxvals.get(idxvals.size()-1)-idxvals.get(0)+1 > idxvals.size()) {
                // There are holes in the index domain.
                HashMap<Long,Long> mapping=new HashMap<Long,Long>();
                for(int i=new_lb; i<idxvals.size()+new_lb; i++) {
                    mapping.put(idxvals.get(i), (long)i);
                }
                offsets.add(null);
                maps.add(mapping);
                newidxdoms.add(new IntegerDomain(new Range(new NumberConstant(new_lb), new NumberConstant(idxvals.size()+new_lb-1))));
            }
            else {
                long lowervalue=idxvals.get(0);
                long uppervalue=idxvals.get(idxvals.size()-1);
                offsets.add(-lowervalue+new_lb);
                maps.add(null);
                newidxdoms.add(new IntegerDomain(new Range(new NumberConstant(new_lb), new NumberConstant(uppervalue-lowervalue+new_lb))));
            }
        }
        MatrixDomain new_md=new MatrixDomain(md.getBaseDomain(), newidxdoms);
        
        m.global_symbols.setDomain(matrixname, new_md);
        
        // If it is a constant matrix:
        if(m.global_symbols.getCategory(matrixname)==ASTNode.Constant) {
            // Shift the indices inside the constant matrix. 
            ASTNode cm=m.global_symbols.getConstantMatrix(matrixname);
            
            m.global_symbols.setConstantMatrix(matrixname, shiftConstantMatrix(cm,newidxdoms));
            
        }
        
    }
    
    ASTNode shiftConstantMatrix(ASTNode matin, ArrayList<ASTNode> newidxdoms) {
        if(newidxdoms.size()==0) {
            assert matin.getDimension()==0;
            return matin;
        }
        else if(matin instanceof CompoundMatrix) {
            ArrayList<ASTNode> matout=new ArrayList<ASTNode>();
            
            ArrayList<ASTNode> newidxdoms_inner=new ArrayList<ASTNode>(newidxdoms);
            newidxdoms_inner.remove(0);
            
            for(int i=1; i<matin.numChildren(); i++) {
                matout.add(shiftConstantMatrix(matin.getChild(i), newidxdoms_inner));
            }
            
            return new CompoundMatrix(newidxdoms.get(0), matout);
        }
        else {
            assert matin instanceof EmptyMatrix;
            return matin;
        }
    }
    
	protected NodeReplacement processNode(ASTNode curnode)
	{
	    if( (curnode instanceof MatrixDeref || curnode instanceof SafeMatrixDeref) 
	        && (curnode.getParent()==null || !(curnode.getParent() instanceof Tag))  // Not tagged.
	        && curnode.getChild(0) instanceof Identifier)  
	    {
	        String name=curnode.getChild(0).toString();
	        if(name.equals(matrixname)) {
                ArrayList<ASTNode> new_indices=new ArrayList<ASTNode>();
                for(int i=0; i<curnode.numChildren()-1; i++)
                {
                    if(offsets.get(i)==null) {
                        new_indices.add(new Mapping(maps.get(i), curnode.getChild(i+1)));
                    }
                    else {
                        new_indices.add(BinOp.makeBinOp("+", curnode.getChild(i+1), new NumberConstant(offsets.get(i))));
                    }
                }
                
                if(curnode instanceof MatrixDeref) {
                    return new NodeReplacement(new Tag(new MatrixDeref(curnode.getChild(0), new_indices),true));
                }
                else {
                    return new NodeReplacement(new Tag(new SafeMatrixDeref(curnode.getChild(0), new_indices),true));
                }
	        }
	    }
	    
	    if(curnode instanceof MatrixSlice && (curnode.getParent()==null || !(curnode.getParent() instanceof Tag))
	        && curnode.getChild(0) instanceof Identifier )
	    {
	        ArrayList<ASTNode> curnode_children=curnode.getChildren();
	        String name=((Identifier)curnode_children.get(0)).getName();
	        if(name.equals(matrixname)) {
                ArrayList<ASTNode> new_indices=new ArrayList<ASTNode>();
                
                for(int i=0; i<curnode_children.size()-1; i++)
                {
                    ASTNode old_index=curnode_children.get(i+1);
                    assert old_index.getCategory()!=ASTNode.Decision;   // This is type-checking and should be done before here.
                    
                    if(old_index instanceof Range)
                    {
                        for(int j=0; j<old_index.numChildren(); j++) {
                            if(offsets.get(i)!=null) {
                                old_index.setChild(j, BinOp.makeBinOp("+", old_index.getChild(j), new NumberConstant(offsets.get(i))));
                            }
                            else {
                                old_index.setChild(j, new Mapping(maps.get(i), old_index.getChild(j)));
                            }
                        }
                        new_indices.add(old_index);
                    }
                    else if(old_index.isSet())
                    {
                        new_indices.add(old_index);
                    }
                    else
                    {
                        // Should really type check it as an arithmetic expression
                        if(offsets.get(i)!=null) {
                            new_indices.add(BinOp.makeBinOp("+", old_index, new NumberConstant(offsets.get(i))));
                        }
                        else {
                            new_indices.add(new Mapping(maps.get(i), old_index));
                        }
                    }
                }
                return new NodeReplacement(new Tag(new MatrixSlice(curnode_children.get(0), new_indices, m.global_symbols),true));
	        }
	    }
	    return null;
	}
	
	
}
