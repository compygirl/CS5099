package savilerow.expression;
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


import java.util.*;
import savilerow.model.*;
import savilerow.*;

public class MatrixSlice extends ASTNode
{
    public static final long serialVersionUID = 1L;
    private SymbolTable global_symbols;
    // First child is a reference to the matrix.
    // Other children are InfiniteIntegerDomain ("..") or
    // arithmetic expression of anything except decision variables. 
    
    public MatrixSlice(ASTNode id, ArrayList<ASTNode> ran, SymbolTable st)
    {
        ArrayList<ASTNode> ch=new ArrayList<ASTNode>();
        ch.add(id); ch.addAll(ran);
        setChildren(ch);
        global_symbols=st;
    }
    
	public ASTNode copy()
	{
	    ASTNode id=getChild(0);
	    ArrayList<ASTNode> ran=getChildren();
	    ran.remove(0);
	    return new MatrixSlice(id, ran, global_symbols);
	}
	public boolean typecheck(SymbolTable st) {
	    for(ASTNode child :  getChildren()) {
	        if(!child.typecheck(st))
	            return false;    
	    }
	    for(int i=1; i<numChildren(); i++) {
	        if(getChild(i).getCategory()>ASTNode.Quantifier) {
	            //CmdFlags.println("ERROR: Matrix slice may not contain decision variables in indices:");
	            //CmdFlags.println("ERROR: "+this);
	            //return false;
	        }
	    }
	    if(numChildren()-1 != getChild(0).getDimension()) {
	        CmdFlags.println("ERROR: Number of indices in matrix slice does not match dimensions of matrix:");
            CmdFlags.println("ERROR: "+this);
            return false;
	    }
	    return true;
	}
	
	public ASTNode simplify() {
	    if(numChildren()==2 && getChild(1) instanceof Unpack) {
	        ArrayList<ASTNode> indices=((Unpack)getChild(1)).items();
	        // Unpack it.
	        if(indices!=null) {
	            return new MatrixSlice(getChild(0), indices, global_symbols);
	        }
	        return this;
	    }
	    
	    // Check the index sets
        // If they are all constant then attempt to evaluate the MatrixSlice.
        // If there is a decision expression in an index then MatrixSlice will be rewritten into matrix comprehension(s).
        boolean all_const=true;
        boolean decisionvar=false;
        for(int i=1; i<numChildren(); i++) {
            if(getChild(i).getCategory()>ASTNode.Constant) {
                all_const=false;
            }
            if(getChild(i).getCategory()==ASTNode.Decision) {
                decisionvar=true;
            }
        }
        
        if(all_const) {
            // Simplify if first child is a matrix literal. 
            if(getChild(0) instanceof CompoundMatrix || getChild(0) instanceof EmptyMatrix) {
                ArrayList<ASTNode> restindices=getChildren();
                restindices.remove(0);
                ASTNode eval=evaluateSlice(getChild(0), restindices);
                return (eval==null)?this:eval;
            }
            
            // Simplify if first child is a matrix literal  in the symbol table. 
            if(getChild(0) instanceof Identifier) {
                SymbolTable st=((Identifier)getChild(0)).global_symbols;
                
                String matid=getChild(0).toString();
                if(st.getCategory(matid)==ASTNode.Constant) {
                    ASTNode a=st.getConstantMatrix(matid);
                    
                    ArrayList<ASTNode> restindices=getChildren();
                    restindices.remove(0);
                    
                    ASTNode eval=evaluateSlice(a, restindices);
                    return (eval==null)?this:eval;
                }
                
            }
        }
        
        if(decisionvar) {
            // Rewrite into matrix comprehensions (to construct the dimensions with ..) and matrix indexing.
            
            ArrayList<ASTNode> comprehensionvars=list();
            ArrayList<ASTNode> matderef=list();
            
            for(int i=1; i<numChildren(); i++) {
                if(getChild(i).isSet()) {
                    ASTNode newid=new Identifier(global_symbols.newAuxId(), global_symbols);  //  Make a new unique variable name.
                    comprehensionvars.add(newid);
                    matderef.add(newid);
                }
                else {
                    comprehensionvars.add(null);
                    matderef.add(getChild(i));
                }
            }
            
            ASTNode tmp=new SafeMatrixDeref(getChild(0), matderef);
            
            // Now wrap the matrix deref with comprehensions to construct the slice dimensions.
            
            // Need matrix dimensions to build the comprehensions. 
            ASTNode matindices=new Indices(getChild(0));
            
            for(int i=numChildren()-1; i>=1; i--) {
                if(getChild(i).isSet()) {
                    ASTNode indexdom=new MatrixDeref(matindices, list(new NumberConstant(i)));  // Get the index domain for this dimension from the list of indices.
                    
                    //  Wrap tmp with a comprehension to construct dimension i. 
                    
                    tmp=new ComprehensionMatrix(tmp, list(new ComprehensionForall(comprehensionvars.get(i-1), indexdom)), new BooleanConstant(true), indexdom);
                }
            }
            return tmp;
        }
        
	    return this;
	}
	
	// recursively evaluate the slice. Returns null if the slice is undefined. 
	public static ASTNode evaluateSlice(ASTNode compound_matrix, ArrayList<ASTNode> indices) {
	    if(indices.size()==0) {
	        assert !(compound_matrix instanceof CompoundMatrix || compound_matrix instanceof EmptyMatrix);
	        return compound_matrix;
	    }
	    
	    ASTNode sliceindex=indices.get(0);
	    
	    if(compound_matrix instanceof EmptyMatrix) {
            // Construct new matrix domain for empty matrix.
            // First get the matrix domain for compound_matrix (which is itself an empty matrix)
            ASTNode matdom=compound_matrix.getChild(0);
            
            // Get indices
            ArrayList<ASTNode> empty_idx=matdom.getChildren();
            empty_idx.remove(0); empty_idx.remove(0); empty_idx.remove(0);
            
            assert empty_idx.size()==indices.size();
            
            for(int i=empty_idx.size()-1; i>=0; i--) {
                if(! indices.get(i).equals(new IntegerDomain(new Range(null, null)))) {
                    // If indices.get(i) is a constant, remove that dimension.  
                    empty_idx.remove(i);
                }
            }
            
            return new EmptyMatrix(new MatrixDomain(compound_matrix.getChild(0).getChild(0), // get original base domain. 
                empty_idx));
        }
	    
        // Must be a compound matrix from here on. 
        
	    if(sliceindex.equals(new IntegerDomain(new Range(null, null)))) {
	        ArrayList<ASTNode> newcm=new ArrayList<ASTNode>();
            
            ArrayList<ASTNode> restindices=new ArrayList<ASTNode>(indices);
            restindices.remove(0);
            
            for(int i=1; i<compound_matrix.numChildren(); i++) {
                ASTNode tmp=evaluateSlice(compound_matrix.getChild(i), restindices);
                if(tmp==null) return null;
                newcm.add(tmp);
            }
            return new CompoundMatrix(compound_matrix.getChild(0), newcm);   // Take index domain from original CM.
	    }
	    else {
	        // This index must be a constant
	        ArrayList<Long> cmindex;
            if(compound_matrix instanceof CompoundMatrix) {
                cmindex=compound_matrix.getChild(0).getValueSet();
            }
            else if (compound_matrix instanceof EmptyMatrix) {
                cmindex=new ArrayList<Long>();
            }
            else {
                cmindex=null;
            }
	        
	        long val=sliceindex.getValue();
	        int idxval=cmindex.indexOf(val);
	        
	        if(idxval==-1) {
	            // Matrix is indexed out of range.  Undefined. Delay evaluation. 
	            return null;
	        }
	        
            ArrayList<ASTNode> restindices=new ArrayList<ASTNode>(indices);
            restindices.remove(0);
	        
	        return evaluateSlice(compound_matrix.getChild(idxval+1), restindices);
	    }
	}
	
	public void toMinion(StringBuffer b, boolean bool_context)
	{
	    assert !bool_context;
	    getChild(0).toMinion(b, false);
	    b.append("[");
	    for(int i=1; i<numChildren(); i++)
	    {
	        getChild(i).toMinion(b, false);
	        if(i<numChildren()-1) b.append(",");
	    }
	    b.append("]");
	}
	public void toDominionInner(StringBuffer b, boolean bool_context) {
	    toDominionParam(b); // This is OK because the indices must be parameter/quantifier expressions or constants
	}
	public void toDominionParam(StringBuffer b) {
	    getChild(0).toDominionParam(b);
	    b.append("[");
	    for(int i=1; i<numChildren(); i++) {
	        getChild(i).toDominionParam(b);
	        if(i<numChildren()-1) b.append(",");
	    }
	    b.append("]");
	}
	public Intpair getBounds() {
	    return getChild(0).getBounds();
	}
	public PairASTNode getBoundsAST() {
	    return getChild(0).getBoundsAST();   // Identifier class deals appropriately with getting bounds of matrix identifiers.
	}
	@Override
	public int getDimension() {
	    int count=0;
	    for(int i=1; i<numChildren(); i++) {
	        if(getChild(i).isSet()) {
	            count++;
	        }
	    }
	    return count;
	}
	
	public String toString() {
	    String st=getChild(0).toString()+"[";
	    for(int i=1; i<numChildren(); i++) {
	        st=st+getChild(i);
	        if(i<numChildren()-1) st+=",";
	    }
	    return st+"]";
	}
	
	// Element requires this. 
	public boolean isRelation() {
	    return getChild(0).isRelation();
	}
	public boolean isNumerical() {
	    return getChild(0).isNumerical();
	}
}
