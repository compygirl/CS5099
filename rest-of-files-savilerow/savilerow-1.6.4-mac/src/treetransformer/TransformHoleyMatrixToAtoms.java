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
//  Transform a matrix of decision variables into individual decision vars, and 
// all matrixderefs, slices and atomic identifiers need to be changed as well.

public class TransformHoleyMatrixToAtoms extends TreeTransformerBottomUpNoWrapper
{
    String matname;
    ASTNode matid;
    
    // Tree of CompoundMatrixIndex, branches for each index. At the leaves, identifiers for the 
    // different variables.
    ASTNode matrixvars;
    
    int dimensions;
    
    public TransformHoleyMatrixToAtoms(String mname, Model mod) {
        super(mod);
        matname=mname;
        
        matid=new Identifier(matname, m.global_symbols);
        
        ASTNode holeymatdom=m.global_symbols.getDomain(matname);
        dimensions=holeymatdom.getChild(0).numChildren()-3;
        
        matrixvars=populate_matrixvars(holeymatdom.getChild(1), new ArrayList<ASTNode>());
        
        m.global_symbols.deleteMatrix(matname);   // Not just a straightforward deletion, need to keep it for parsing
    }
    
    // make CompoundMatrix
    // Recursion strips off one forAll quantifier. 
    protected ASTNode populate_matrixvars(ASTNode forallfind, ArrayList<ASTNode> indices) {
        //System.out.println(forallfind);
        if(forallfind instanceof Find) {
            // Base case.
            // Make a new variable.
            String newname=matname;
            for(int i=0; i<indices.size(); i++) {
                String fmtint=String.format("%05d",indices.get(i).getValue());
                fmtint=fmtint.replaceAll("-","n");
                newname+="_"+fmtint;
            }
            
            ArrayList<Long> indices_int=new ArrayList<Long>();
            for(int i=0; i<indices.size(); i++) indices_int.add(indices.get(i).getValue());
            
            m.global_symbols.newVariable(newname, forallfind.getChild(1).copy(), ASTNode.Decision, matid, indices_int);
            return new Identifier(newname,m.global_symbols);
        }
        else {
            // it's a forAll, strip off the first identifier.
            ASTNode id=forallfind.getChild(0).getChild(0);
            ArrayList<Long> vals=forallfind.getChild(1).getChild(0).getValueSet();
            ArrayList<ASTNode> vals_astnode=new ArrayList<ASTNode>();
            
            ASTNode innerexp_outer=forallfind.getChild(2).copy();
            
            ASTNode condition_outer;
            if(forallfind.numChildren()>3) {
                condition_outer=forallfind.getChild(3);
            }
            else {
                condition_outer=new BooleanConstant(true);
            }
            
            if( forallfind.getChild(0).numChildren()>1 ) {
                ArrayList<ASTNode> idsleft=forallfind.getChild(0).getChildren();
                idsleft.remove(0);
                ArrayList<ASTNode> domsleft=forallfind.getChild(1).getChildren();
                domsleft.remove(0);
                for(int i=idsleft.size()-1; i>=0; i--) {
                    innerexp_outer=new ForallExpression(idsleft.get(i), domsleft.get(i), innerexp_outer, condition_outer);
                    condition_outer=new BooleanConstant(true);  // after first iteration, remove the condition
                }
                condition_outer=new BooleanConstant(true);   // Don't try to evaluate the condition until all the quantifier vars are set.
            }
            ArrayList<ASTNode> accumulate=new ArrayList<ASTNode>();
            for(int i=0; i<vals.size(); i++) {
                long val=vals.get(i);
                ReplaceASTNode r=new ReplaceASTNode(id, new NumberConstant(val));
                ASTNode innerexp=r.transform(innerexp_outer.copy());
                TransformSimplify ts=new TransformSimplify();
                innerexp=ts.transform(innerexp);
                ASTNode condition=r.transform(condition_outer.copy());
                condition=ts.transform(condition);
                assert condition instanceof BooleanConstant;
                if(condition.equals(new BooleanConstant(true))) {
                    vals_astnode.add(new NumberConstant(val));  // add it to the index set. 
                    ArrayList<ASTNode> indices_copy=new ArrayList<ASTNode>(indices);
                    indices_copy.add(new NumberConstant(val));
                    accumulate.add(populate_matrixvars(innerexp, indices_copy));
                }
            }
            
            // May not retain all the indices/ correct base domain here, when the current dimension is empty.
            return CompoundMatrix.makeCompoundMatrix(new IntegerDomain(vals_astnode), accumulate, false);  
        }
    }
    
    protected NodeReplacement processNode(ASTNode curnode) {
        if( (curnode instanceof MatrixDeref || curnode instanceof SafeMatrixDeref)
            && curnode.getChild(0).equals(matid) ) {
            ArrayList<ASTNode> idxs=curnode.getChildren();
            idxs.remove(0);
            
            // Look up identifier
            ASTNode temp=matrixvars;
            for(int i=0; i<idxs.size(); i++) {
                assert temp instanceof CompoundMatrix;
                ArrayList<Long> idxset=temp.getChild(0).getValueSet();
                long idx=idxs.get(i).getValue();
                
                if(idxset.indexOf(idx)==-1) {
                    if(curnode instanceof MatrixDeref) {
                        CmdFlags.println("WARNING: When constructing holey matrix, found matrix dereference: "+curnode);
                        CmdFlags.println("WARNING: that is outside the matrix. ");
                    }
                    return new NodeReplacement(new NumberConstant(0));  // default value or placeholder.
                }
                
                temp=temp.getChild(1).getChild(idxset.indexOf(idx));
            }
            
            assert temp instanceof Identifier;
	        return new NodeReplacement(temp);
        }
        if(curnode instanceof MatrixSlice && curnode.getChild(0).equals(matid) ) {
            ArrayList<ASTNode> slice=curnode.getChildren();
            slice.remove(0);
            
            ASTNode cm=collectSlice(matrixvars, slice, 0);
            return new NodeReplacement(cm);
        }
        if(curnode instanceof Identifier && curnode.equals(matid)
            && !(curnode.getParent() instanceof MatrixDeref)
            && !(curnode.getParent() instanceof SafeMatrixDeref)
            && !(curnode.getParent() instanceof MatrixSlice)
            ) {
            // like a slice M[_,_,_]... 
            
            ArrayList<ASTNode> slice=new ArrayList<ASTNode>();
            for(int i=0; i<dimensions; i++) slice.add(new IntegerDomain(new Range(null, null)));
            
            ASTNode cm=collectSlice(matrixvars, slice, 0);
            
            return new NodeReplacement(cm);
        }
        return null;
    }
    
    // Make nested CompoundMatrices of Identifiers
    ASTNode collectSlice(ASTNode mat, ArrayList<ASTNode> slice, int sliceidx) {
        if(sliceidx==slice.size()) {
            assert mat instanceof Identifier;
            return mat;  // just return the identifier.
        }
        else {
            assert mat instanceof CompoundMatrix;
            ASTNode sl=slice.get(sliceidx);
            
            ArrayList<ASTNode> cm=new ArrayList<ASTNode>();
            
            ArrayList<Long> vals=mat.getChild(0).getValueSet();
            for(int i=0; i<vals.size(); i++) {
                long val=vals.get(i);
                if(sl instanceof NumberConstant || sl instanceof BooleanConstant) {
                    if(sl.getValue()==val) {
                        cm.add(collectSlice(mat.getChild(1).getChild(i), slice, sliceidx+1));
                    }
                }
                else {
                    if(sl.containsValue(val)) {
                        cm.add(collectSlice(mat.getChild(1).getChild(i), slice, sliceidx+1));
                    }
                }
            }
            
            // May not retain all the indices/ correct base domain here, when the current dimension is empty.
            return CompoundMatrix.makeCompoundMatrix(cm);
        }
    }
}
