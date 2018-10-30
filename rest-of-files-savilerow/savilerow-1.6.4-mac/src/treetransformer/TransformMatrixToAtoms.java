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

// WARNING: this implicitly normalises all indices to be 0-based. 

public class TransformMatrixToAtoms extends TreeTransformerBottomUpNoWrapper
{
    ASTNode matid;
    
    ArrayList<ASTNode> indexdoms;
    
    ASTNode matrixdomain;
    
    ASTNode matrixliteral;
    
    public TransformMatrixToAtoms(String matname, Model _m) {
        super(_m);
        matid=new Identifier(matname, m.global_symbols);
        matrixdomain=m.global_symbols.getDomain(matname);
        
        indexdoms=matrixdomain.getChildren();
        indexdoms.remove(0);indexdoms.remove(0);indexdoms.remove(0);
        
        // Introduce new variables for each entry in the matrix. 
        enumerateMatrix(new ArrayList<ASTNode>(indexdoms), matname, matrixdomain.getChild(0), matid, new ArrayList<Long>());
        
        m.global_symbols.deleteMatrix(matname);   // Not just a straightforward deletion, need to keep it for parsing
        
        
        // Make a matrix literal for the whole matrix.
        matrixliteral=enumerateMatrixLiteral(indexdoms, matname, m, matrixdomain.getChild(0).isBooleanSet());
        
    }
    
    //  create vars M_1_1 for M[1,1]
    private void enumerateMatrix(ArrayList<ASTNode> idxdoms, String build, ASTNode basedom, ASTNode baseid, ArrayList<Long> indices) {
        if(idxdoms.size()==0) {
            m.global_symbols.newVariable(build, basedom.copy(), ASTNode.Decision, baseid, indices);
        }
        else {
            ArrayList<ASTNode> localindexdoms=new ArrayList<ASTNode>(idxdoms);
            ASTNode idx=localindexdoms.remove(0);
            ArrayList<Long> valset=idx.getValueSet();
            for(int i=0; i<valset.size(); i++) {
                String fmtint=String.format("%05d",valset.get(i));
                String newbuild=build+"_"+fmtint;
                indices.add(valset.get(i));
                enumerateMatrix(localindexdoms, newbuild, basedom, baseid, indices);
                indices.remove(indices.size()-1);
            }
        }
    }
    
    // Build a matrix literal for the whole matrix. 
    public static ASTNode enumerateMatrixLiteral(ArrayList<ASTNode> idxdoms, String build, Model m, boolean isBool) {
        if(idxdoms.size()==0) {
            return new Identifier(build, m.global_symbols);
        }
        else {
            ArrayList<ASTNode> localindexdoms=new ArrayList<ASTNode>(idxdoms);
            ASTNode idx=localindexdoms.remove(0);
            ArrayList<Long> valset=idx.getValueSet();
            
            ArrayList<ASTNode> compoundmatrix=new ArrayList<ASTNode>();
            for(int i=0; i<valset.size(); i++) {
                String fmtint=String.format("%05d",valset.get(i));
                String newbuild=build+"_"+fmtint;
                compoundmatrix.add(enumerateMatrixLiteral(localindexdoms, newbuild, m, isBool));
            }
            if(compoundmatrix.size()>0) {
                return new CompoundMatrix(idx, compoundmatrix);  // Make new cm with index domain
            }
            else {
                // Empty in this dimension. 
                ASTNode basedom=isBool?new BooleanDomain(new EmptyRange()):new IntegerDomain(new EmptyRange());
                return new EmptyMatrix(new MatrixDomain(basedom, new ArrayList<ASTNode>(idxdoms)));
            }
        }
    }
    
    // New implementation making use of matrixliteral and methods in MatrixSlice and MatrixDeref
    protected NodeReplacement processNode(ASTNode curnode) {
        
        // Slice
        if(curnode instanceof MatrixSlice && curnode.getChild(0).equals(matid) ) {
            ArrayList<ASTNode> slice=curnode.getChildren();
            slice.remove(0);
            
            ASTNode cm=MatrixSlice.evaluateSlice(matrixliteral, slice); 
            
            if(cm==null) {
                // Slice cannot be evaluated because some index is out of bounds.
                // Put a copy of the matrix literal into the slice and leave it un-evaluated.
                return new NodeReplacement(new MatrixSlice(matrixliteral, slice, m.global_symbols));
            }
            
            return new NodeReplacement(cm);
        }
        
        //  Deref
        if( (curnode instanceof MatrixDeref || curnode instanceof SafeMatrixDeref) 
	        && curnode.getChild(0).equals(matid) ) {
	        
	        ArrayList<ASTNode> indices=curnode.getChildren();
	        indices.remove(0);
	        
	        ASTNode df=null;
	        
	        boolean constIndices=true;
	        for(int i=1; i<curnode.numChildren(); i++) {
	            if(! curnode.getChild(i).isConstant() ) {
	                constIndices=false;
	                break;
	            }
	        }
	        
	        if(constIndices) {
                if(curnode instanceof MatrixDeref) {
                    df=MatrixDeref.derefCompoundMatrix(matrixliteral, indices);
                }
                else {
                    df=SafeMatrixDeref.derefCompoundMatrix(matrixliteral, indices);
                }
	        }
	        
	        if(df==null) {
	            // Deref cannot be evaluated because some index is out of bounds, or some index contains a decision variable. 
	            // Put a new copy of the matrix literal into the deref and leave it unevaluated.
	            if(curnode instanceof MatrixDeref) {
	                return new NodeReplacement(new MatrixDeref(matrixliteral, indices));
	            }
	            else {
	                return new NodeReplacement(new SafeMatrixDeref(matrixliteral, indices));
	            }
            }
            
            return new NodeReplacement(df);
        }
        
        // Neither slice nor deref
        if(curnode instanceof Identifier && curnode.equals(matid)
            && !(curnode.getParent() instanceof MatrixDeref)
            && !(curnode.getParent() instanceof SafeMatrixDeref)
            && !(curnode.getParent() instanceof MatrixSlice)
            ) {
            return new NodeReplacement(matrixliteral);
        }
        return null;
    }
    
}
