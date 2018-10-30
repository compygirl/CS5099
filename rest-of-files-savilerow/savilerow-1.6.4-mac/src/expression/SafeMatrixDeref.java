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
import savilerow.model.SymbolTable;
import savilerow.*;

// With a default value of 0.

public class SafeMatrixDeref extends ASTNode
{
    public static final long serialVersionUID = 1L;
    // The first child is a matrix reference, the rest are indices.
    public SafeMatrixDeref(ASTNode mat, ArrayList<ASTNode> ind)
    {
        ArrayList<ASTNode> ch=new ArrayList<ASTNode>();
        ch.add(mat); ch.addAll(ind);
        setChildren(ch);
    }
    
	public ASTNode copy()
	{
	    ASTNode id=getChild(0);
	    ArrayList<ASTNode> ran=getChildren();
	    ran.remove(0);
	    return new SafeMatrixDeref(id, ran);
	}
	
	public boolean isRelation() {
	    // Is first child a matrix of bool
	    return getChild(0).isRelation();
	}
	public boolean isNumerical() {
        return !getChild(0).isRelation() && !getChild(0).isSet();
    }
    public boolean isSet() {
        return getChild(0).isSet();
    }
    
	public Intpair getBounds()
	{
	    Intpair a=getChild(0).getBounds();
	    if(a.lower>0) a.lower=0;  // Add the default value.
	    if(a.upper<0) a.upper=0;
	    return a;
	}
	public PairASTNode getBoundsAST() 
	{
	    PairASTNode a=getChild(0).getBoundsAST();
	    a.e1=new Min(a.e1, new NumberConstant(0));
	    a.e2=new Max(a.e2, new NumberConstant(0));
	    return a;
	}
	
	
	public ASTNode simplify() {
	    if(numChildren()==2 && getChild(1) instanceof Unpack) {
	        ArrayList<ASTNode> indices=((Unpack)getChild(1)).items();
	        // Unpack it.
	        if(indices!=null) {
	            return new SafeMatrixDeref(getChild(0), indices);
	        }
	        return this;
	    }
	    
	    boolean hasVariableIndices=false;
        for(int i=1; i<numChildren(); i++) {
            if(getChild(i).getCategory()!=ASTNode.Constant) hasVariableIndices=true;
        }
	    
	    if(!hasVariableIndices) {
	        if(getChild(0) instanceof Identifier) {
	            SymbolTable st=((Identifier)getChild(0)).global_symbols;
                // If there are no variable indices, and the matrix is a constant matrix,
                // then replace with the value.
                
                String matid=getChild(0).toString();
                if(st.getCategory(matid)==ASTNode.Constant) {
                    ASTNode a=st.getConstantMatrix(matid);
                    
                    ArrayList<ASTNode> indices=getChildren();
                    indices.remove(0);
                    
                    ASTNode df=SafeMatrixDeref.derefCompoundMatrix(a, indices);
                    
                    if(df==null) return this;
                    else return df.copy();
                }
            }
            if(getChild(0) instanceof CompoundMatrix || getChild(0) instanceof EmptyMatrix) {
                ArrayList<ASTNode> indices=getChildren();
                indices.remove(0);
                
                ASTNode df=SafeMatrixDeref.derefCompoundMatrix(getChild(0), indices);
                
                if(df==null) return this;
                else return df;
            }
        }
        return this;
	}
	
	public static ASTNode derefCompoundMatrix(ASTNode cm, ArrayList<ASTNode> indices) {
        for(int i=0; i<indices.size(); i++) {
            if(! (cm instanceof CompoundMatrix || cm instanceof EmptyMatrix) ) {
                // Can't deref cm any further. 
                return null;
            }
            if(cm instanceof EmptyMatrix) return new NumberConstant(0);  // Default value.
            
            // From here on its a CompoundMatrix.
            ASTNode idxdom=cm.getChild(0);
            long idx=indices.get(i).getValue();
            
            ArrayList<Intpair> intervalset=idxdom.getIntervalSet();
            int childidx=-1;   /// out of bounds
            int cumulativeindex=0;
            for(int j=0; j<intervalset.size(); j++) {
                Intpair p=intervalset.get(j);
                if( idx>=p.lower && idx<=p.upper) {
                    childidx=(int) (idx-p.lower+cumulativeindex);
                    break;
                }
                cumulativeindex+=p.upper-p.lower+1;
            }
            
            if(childidx==-1) {
                // Out of bounds
                return new NumberConstant(0);  // Default value. 
            }
            int childno=childidx+1;
            
            // Actually do the deref. 
            cm=cm.getChild(childno);
        }
        
        return cm;
	}
	
	public boolean typecheck(SymbolTable st) {
	    for(int i=0; i<numChildren(); i++) {
	        if(! getChild(i).typecheck(st)) return false;
	    }
	    
	    // Check right number of dimensions.
	    if(getChild(0).getDimension() != numChildren()-1) {
	        System.out.println("ERROR: Dimension mismatch in matrix deref: "+this);
            return false;
	    }
	    
	    // check type of each index -- must be numerical or relational.
	    for(int i=1; i<numChildren(); i++) {
	        if( !getChild(i).isNumerical() && !getChild(i).isRelation() ) {
	            System.out.println("ERROR: In matrix deref "+this+", index "+getChild(i)+" is not numerical or relational.");
	            return false;
	        }
	    }
	    return true;
	}
	
	//  WARNING --- All these output methods are inaccurate because they lose the
	// 'safe' default value. 
	
	// Minion might break on this 
    // even though it will be guarded by constraints that the indices are in bounds
	public void toMinion(StringBuffer b, boolean bool_context)
	{
	    if(bool_context) {
	        // If it's contained in and or or, stick it inside an equal constraint.
	        if(CmdFlags.getUseBoundVars() && this.exceedsBoundThreshold() ) {
	            b.append("eq(");
	        }
	        else {
	            b.append("w-literal(");
	        }
	    }
	    
	    getChild(0).toMinion(b, false);
	    b.append("[");
	    for(int i=1; i<numChildren(); i++) {
	        getChild(i).toMinion(b, false);
	        if(i<numChildren()-1) b.append(",");
	    }
	    b.append("]");
	    
	    if(bool_context) {
	        b.append(",1)");
	    }
	}
	
	public String toString() {
	    String st=getChild(0).toString()+"[";
	    for(int i=1; i<numChildren(); i++) { 
	        st=st+getChild(i);
	        if(i<numChildren()-1) st+=", ";
	    }
	    return st+"]";
	}
	
	public void toDominionInner(StringBuffer b, boolean bool_context) {
	    if(bool_context) {
	        // Write it as a constraint
	        b.append(CmdFlags.getCtName()+" ");
	        b.append("literal(");
	    }
	    getChild(0).toDominion(b, false);
	    b.append("[");
	    for(int i=1; i<numChildren(); i++) {
	        getChild(i).toDominion(b, false);
	        if(i<numChildren()-1) b.append(",");
	    }
	    b.append("]");
	    
	    if(bool_context) {
	        b.append(", 1)");
	    }
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
	
	public void toMinizinc(StringBuffer b, boolean bool_context)
	{
	    getChild(0).toMinizinc(b, bool_context);
	    b.append("[");
	    for(int i=1; i<numChildren(); i++) {
	        getChild(i).toMinizinc(b, false);
	        if(i<numChildren()-1) b.append(",");
	    }
	    b.append("]");
	}
}
