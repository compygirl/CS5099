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

import savilerow.*;
import java.util.*;
import java.math.*;
import java.io.*;
import savilerow.model.SymbolTable;
import savilerow.model.Sat;

public class Times extends ASTNode
{
    public static final long serialVersionUID = 1L;
	public Times(ASTNode l, ASTNode r)
	{
		super(l, r);
	}
	
	public Times(ArrayList<ASTNode> args) {
	    super(args);
	}
	
	public ASTNode copy()
	{
	    return new Times(getChildren());
	}
	
	// Turn it into a constant or sum if possible.
	// Turning it into a WeightedSum allows it to be gathered into the parent, if the
	// parent is a WeightedSum.
	// Does not gather children that are also Times, because for output most systems
	// will not have a variable-length multiplication. 
	public ASTNode simplify()
	{
	    ArrayList<ASTNode> c=getChildren();
	    boolean changed=false;
	    
	    long constants=1;
	    for(int i=0; i<c.size(); i++) {
	        if(c.get(i).isConstant()) {
	            if(constants!=1) {
	                changed=true;  // Only set the flag if this is the second non-1 constant found. 
	            }
	            constants=multiply(constants, c.get(i).getValue());
	            c.remove(i);
	            i--;
	        }
	        else if( (!CmdFlags.getOutputReady()) && c.get(i) instanceof Times) {
	            // We are not in "output ready" mode,
	            // Collect it into here. 
	            changed=true;
	            c.addAll(c.get(i).getChildren());
	            c.remove(i);
	            i--;
	        }
	    }
	    
	    if(constants==0) {
	        // Special case, if the product contains a 0 throw it all away
	        return new NumberConstant(0);
	    }
	    
	    if(c.size()==0) {
	        return new NumberConstant(constants);
	    }
	    
	    if(c.size()==1) {
	        // If there is only one non-constant left. 
	        if(constants==1) {
	            return c.get(0);
	        }
	        else {
                ArrayList<Long> l=new ArrayList<Long>();
                l.add(constants);
                return new WeightedSum(c, l);
            }
	    }
	    
	    if(constants!=1) {
	        c.add(new NumberConstant(constants));
	    }
	    
	    if(changed) {
	        return new Times(c);
	    }
	    
	    return this;
	}
	
	@Override
	public boolean typecheck(SymbolTable st) {
	    for(int i=0; i<numChildren(); i++) {
	        if(!getChild(i).typecheck(st)) return false;
	        if(getChild(i).getDimension()>0) {
	            CmdFlags.println("ERROR: Unexpected matrix in product: "+this);
	            return false;
	        }
	        if(getChild(i).isSet()) {
	            CmdFlags.println("ERROR: Unexpected set in product: "+this);
	            return false;
	        }
        }
        return true;
    }
	
	@Override
	public ASTNode normalise() {
	    // sort by hashcode 
        // Insertion sort -- behaves well with almost-sorted lists
        ArrayList<ASTNode> ch=getChildren();
        boolean changed=sortByHashcode(ch);
        
        if(changed) return new Times(ch);
        else return this;
	}
	
	public long getValue() {
	    long eval=1;
	    for(int i=0; i<numChildren(); i++) {
	        assert getChild(i).isConstant();
	        eval=multiply(eval, getChild(i).getValue());
	    }
	    return eval;
	}
	
	static long multiply(long a, long b) {
	    //  Saturates at Long.MAX_VALUE and Long.MIN_VALUE
	    return Intpair.BigIntegerToLong(BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)));
	}
	
	public Intpair getBounds()
	{
	    Intpair a=getChild(0).getBounds();
	    for(int i=1; i<numChildren(); i++) {
            Intpair b=getChild(i).getBounds();
            // multiply the four combinations of bounds
            long w=multiply(a.lower, b.lower);
            long x=multiply(a.upper, b.lower);
            long y=multiply(a.lower, b.upper);
            long z=multiply(a.upper, b.upper);
            a.lower=Math.min(w, Math.min(x, Math.min(y,z)));
            a.upper=Math.max(w, Math.max(x, Math.max(y,z)));
	    }
	    return lookupBounds(a);    //  Look up in FilteredDomainStore
	}
	
	public PairASTNode getBoundsAST() 
	{
	    PairASTNode a=getChild(0).getBoundsAST();
	    for(int i=1; i<numChildren(); i++) {
            PairASTNode b=getChild(i).getBoundsAST();
            // multiply the four combinations of bounds
            ASTNode w=new Times(a.e1, b.e1);
            ASTNode x=new Times(a.e2, b.e1);
            ASTNode y=new Times(a.e1, b.e2);
            ASTNode z=new Times(a.e2, b.e2);
            
            ArrayList<ASTNode> ls=new ArrayList<ASTNode>();
            ls.add(w); ls.add(x); ls.add(y); ls.add(z);
            a.e1=new Min(ls);
            a.e2=new Max(ls);   // will make their own internal copies of ls
        }
        return a;
	}
	
	public boolean toFlatten(boolean propagate) { return true;}
	public boolean isNumerical() {
        return true;
    }
    public boolean isCommAssoc() {
        return true;
    }
	
	public String toString() {
	    String st="(";
	    for(int i=0; i<numChildren(); i++) {
	        st+=getChild(i);
	        if(i<numChildren()-1) st+=" * ";
	    }
	    return st+")";
	}
	
	// Binary output methods. 
	
	public void toMinionWithAuxVar(StringBuffer b, ASTNode aux)	{
	    assert numChildren()==2;
	    b.append("product(");
	    getChild(0).toMinion(b, false);
	    b.append(", ");
	    getChild(1).toMinion(b, false);
	    b.append(", ");
	    aux.toMinion(b, false);
	    b.append(")");
	}
	public void toDominionParam(StringBuffer b) {
	    assert numChildren()==2;
	    b.append("Product(");
	    getChild(0).toDominionParam(b);
	    b.append(",");
	    getChild(1).toDominionParam(b);
	    b.append(")");
	}
	public void toDominionWithAuxVar(StringBuffer b, ASTNode aux)	{
	    assert numChildren()==2;
	    b.append(CmdFlags.getCtName()+" ");
	    b.append("product(");
	    getChild(0).toDominion(b, false);
	    b.append(", ");
	    getChild(1).toDominion(b, false);
	    b.append(", ");
	    aux.toDominion(b, false);
	    b.append(")");
	}
	
	public void toFlatzincWithAuxVar(StringBuffer b, ASTNode aux)	{
	    assert numChildren()==2;
	    b.append("constraint int_times(");
	    getChild(0).toFlatzinc(b, false);
	    b.append(", ");
	    getChild(1).toFlatzinc(b, false);
	    b.append(", ");
	    aux.toFlatzinc(b, false);
	    b.append(");");
	}
	public void toMinizinc(StringBuffer b, boolean bool_context) {
	    assert numChildren()==2;
	    b.append("(");
	    getChild(0).toMinizinc(b, false);
	    b.append("*");
	    getChild(1).toMinizinc(b, false);
	    b.append(")");
	}
	
	////////////////////////////////////////////////////////////////////////////
	//  
	//  SAT encoding
	
    public void toSATWithAuxVar(Sat satModel, ASTNode auxVar) throws IOException {
        satModel.ternaryEncoding(this, auxVar);
    }
    
    public boolean test(long val1, long val2, long aux) {
        return func(val1, val2)==aux;
    }
    public long func(long val1, long val2) {
        return multiply(val1, val2);
    }
    
	public boolean childrenAreSymmetric() {
	    return true;
	}
}