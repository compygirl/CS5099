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

public class TransformMatrixIndicesClass extends TreeTransformerBottomUpNoWrapper
{
    // For a particular matrix, normalise the indices so that they
    // go from 0..n 
    // Works with parameters in the bound expressions
    // Does not compress holes in the indices, unless fully known.
    private int new_lb;
    
    ArrayList<ASTNode> offsets;   // list of offsets to apply.
    ArrayList<HashMap<Long,Long>> maps;    /// List of maps. Each dimension has either a map or an offset. 
    
    String matrixname;
    
    public TransformMatrixIndicesClass(int new_lower_bound, Model mod, String mn)
    {
        super(mod); 
        new_lb=new_lower_bound;
        offsets=new ArrayList<ASTNode>();
        maps=new ArrayList<HashMap<Long,Long>>();
        matrixname=mn;
        
        assert m.global_symbols.getDomain(matrixname) instanceof MatrixDomain;
        MatrixDomain md=(MatrixDomain)m.global_symbols.getDomain(matrixname);
        
        ArrayList<ASTNode> doms=md.getChildren();
        ASTNode basedom=doms.get(0);
        ArrayList<ASTNode> indices=new ArrayList<ASTNode>(doms.subList(3, doms.size()));
        ArrayList<ASTNode> dim_id=md.getChild(1).getChildren();
        
        ArrayList<ASTNode> conditions=new ArrayList<ASTNode>();
        if(md.getChild(2) instanceof And) conditions.addAll(md.getChild(2).getChildren());
        else if(!md.getChild(2).equals(new BooleanConstant(true))) conditions.add(md.getChild(2));
        
        assert indices.size() == dim_id.size()  ||  dim_id.size()==0;
        
        ArrayList<ASTNode> newidxdoms=new ArrayList<ASTNode>();
        TransformSimplify ts=new TransformSimplify();
        for(ASTNode indexdom : indices)
        {
            indexdom=ts.transform(indexdom);
            if(indexdom.getCategory() ==ASTNode.Constant) {
                // Do same as instance-level transformation.
                ArrayList<Long> idxvals=indexdom.getValueSet();
                if(idxvals.get(idxvals.size()-1)-idxvals.get(0)+1 > idxvals.size()) {
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
                    offsets.add(new NumberConstant(-lowervalue+new_lb));
                    maps.add(null);
                    newidxdoms.add(new IntegerDomain(new Range(new NumberConstant(new_lb), new NumberConstant(uppervalue-lowervalue+new_lb))));
                }
            }
            else {
                if(indexdom.getCategory()>ASTNode.Quantifier) CmdFlags.println(indexdom.getCategory());
                assert indexdom.getCategory()<=ASTNode.Quantifier;
                assert indexdom.isFiniteSet();
                // Get the minimum expression.
                ASTNode adjustment=BinOp.makeBinOp("+", new UnaryMinus(indexdom.getBoundsAST().e1), new NumberConstant(new_lb));
                adjustment=ts.transform(adjustment);
                
                offsets.add(adjustment);    // adjust to 0. 
                maps.add(null);
                
                // Construct a new index domain
                
                ArrayList<ASTNode> newbsr=new ArrayList<ASTNode>();
                for(int i=0; i<indexdom.numChildren(); i++) {
                    if(indexdom.getChild(i) instanceof Range) {
                        newbsr.add(new Range(
                            BinOp.makeBinOp("+", indexdom.getChild(i).getChild(0), adjustment),   // Isn't this the same as applyShift??
                            BinOp.makeBinOp("+", indexdom.getChild(i).getChild(1), adjustment)
                        ));
                    }
                    else {
                        // Some kind of parameter expression
                        newbsr.add(BinOp.makeBinOp("+", indexdom.getChild(i), adjustment));
                    }
                    
                    
                }
                newidxdoms.add(new IntegerDomain(newbsr));
            }
        }
        
        // Map the conditions.
        // APPLY THE OPPOSITE of the shift of the indices -- i.e. if the index is adjusted by -2, then +2 is needed here.
        if(conditions.size()>0) {
            assert indices.size()==dim_id.size();
            for(int i=0; i<indices.size(); i++) {
                if(offsets.get(i)!=null) {
                    TreeTransformer t=new ReplaceASTNode2(dim_id.get(i), BinOp.makeBinOp("-", dim_id.get(i), offsets.get(i)));
                    for(int j=0; j<conditions.size(); j++) {
                        conditions.set(j, t.transform(conditions.get(j)));
                    }
                }
                
                if(maps.get(i)!=null) {
                    assert false;   ///    can't do the inverse of a map at the moment!!
                    TreeTransformer t=new ReplaceASTNode2(dim_id.get(i), new Mapping(maps.get(i), dim_id.get(i)));
                    for(int j=0; j<conditions.size(); j++) {
                        conditions.set(j, t.transform(conditions.get(j)));
                    }
                }
            }
        }
        
        MatrixDomain new_md=new MatrixDomain(md.getChild(0), newidxdoms, new Container(dim_id), new And(conditions));
        
        m.global_symbols.setDomain(matrixname, new_md);
    }
    
	protected NodeReplacement processNode(ASTNode curnode)
	{
	    if( (curnode instanceof MatrixDeref || curnode instanceof SafeMatrixDeref) 
	        && (curnode.getParent()==null || !(curnode.getParent() instanceof Tag)))  // Not tagged.
	    {
	        ArrayList<ASTNode> curnode_children=curnode.getChildren();
	        if(curnode.getChild(0) instanceof Identifier) {
                String name=((Identifier)curnode.getChild(0)).getName();
                if(name.equals(matrixname)) {
                    ArrayList<ASTNode> new_indices=new ArrayList<ASTNode>();
                    for(int i=0; i<curnode_children.size()-1; i++)
                    {
                        if(offsets.get(i)==null) {
                            new_indices.add(new Mapping(maps.get(i), curnode_children.get(i+1)));
                        }
                        else {
                            if(CmdFlags.getUseMappers() && curnode_children.get(i+1).getCategory()==ASTNode.Decision) {
                                new_indices.add(new ShiftMapper(curnode_children.get(i+1), offsets.get(i)));
                            }
                            else {
                                new_indices.add(BinOp.makeBinOp("+", curnode_children.get(i+1), offsets.get(i)));
                            }
                        }
                    }
                    
                    if(curnode instanceof MatrixDeref) {
                        return new NodeReplacement(new Tag(new MatrixDeref(curnode_children.get(0), new_indices), true));
                    }
                    else {
                        return new NodeReplacement(new Tag(new SafeMatrixDeref(curnode_children.get(0), new_indices), true));
                    }
                }
	        }
	    }
	    
	    if(curnode instanceof MatrixSlice && (curnode.getParent()==null || !(curnode.getParent() instanceof Tag)))  // Not tagged.
	    {
	        ArrayList<ASTNode> curnode_children=curnode.getChildren();
	        if(curnode.getChild(0) instanceof Identifier) {
                String name=((Identifier)curnode_children.get(0)).getName();
                if(name.equals(matrixname)) {
                    ArrayList<ASTNode> new_indices=new ArrayList<ASTNode>();
                    
                    for(int i=0; i<curnode_children.size()-1; i++)
                    {
                        ASTNode old_index=curnode_children.get(i+1);
                        assert old_index.getCategory()!=ASTNode.Decision;   // This is type-checking and should be done before here.
                        
                        if(old_index instanceof Range)
                        {
                            ArrayList<ASTNode> ch=old_index.getChildren();
                            for(int j=0; j<ch.size(); j++) {
                                if(offsets.get(i)!=null) {
                                    ch.set(j, BinOp.makeBinOp("+", ch.get(j), offsets.get(i)));
                                }
                                else {
                                    ch.set(j, new Mapping(maps.get(i), ch.get(j)));
                                }
                            }
                            old_index.setChildren(ch);
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
                                new_indices.add(BinOp.makeBinOp("+", old_index, offsets.get(i)));
                            }
                            else {
                                new_indices.add(new Mapping(maps.get(i), old_index));
                            }
                        }
                    }
                    return new NodeReplacement(new Tag(new MatrixSlice(curnode_children.get(0), new_indices, m.global_symbols), true));
                }
            }
	    }
	    return null;
	}
	
	
}
