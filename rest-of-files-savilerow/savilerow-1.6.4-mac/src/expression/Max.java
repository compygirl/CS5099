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
import java.io.*;
import savilerow.model.SymbolTable;
import savilerow.*;
import savilerow.model.Sat;

public class Max extends ASTNode
{
    public static final long serialVersionUID = 1L;
	public Max(ArrayList<ASTNode> ch) {
	    super(ch);
	    assert ch.size()>=1;
	}
	public Max(ASTNode a, ASTNode b) {
	    super(a,b);
	}
	
	public ASTNode copy()
	{
	    return new Max(getChildren());
	}
	public boolean isRelation(){return false;}
	public boolean isNumerical(){return true;}
	public boolean toFlatten(boolean propagate){return true;}
	public ASTNode simplify()
	{
	    ArrayList<ASTNode> ch=getChildren();
	    boolean changed=false;
	    
	    for(int i=0; i<ch.size(); i++) {
	        // grab the contents of any Max's inside. 
	        if(ch.get(i) instanceof Max) {
	            ch.addAll(ch.get(i).getChildren());
	            ch.remove(i);
	            i--;
	            changed=true;
	        }
	    }
	    
	    boolean newconst_used=false;
	    long newconst=Long.MIN_VALUE;
	    for(int i=0; i<ch.size(); i++) {
	        if(ch.get(i) instanceof PosInfinity) return ch.get(i);
	        if(ch.get(i).isConstant()) {
	            if(newconst_used) {  // If this is the second constant..
	                changed=true;
	            }
	            long c=ch.get(i).getValue();
	            newconst= (newconst>c)? newconst : c;
	            ch.remove(i);
	            i--;
	            newconst_used=true;
	        }
	    }
	    if(newconst_used) ch.add(new NumberConstant(newconst));
	    
	    // Remove duplicates.
	    for(int i=0; i<ch.size(); i++) {
	        for(int j=i+1; j<ch.size(); j++) {
	            if(ch.get(i).equals(ch.get(j))) {
	                ch.remove(j);
	                j--;
	                changed=true;
	            }
	        }
	    }
	    
	    if(ch.size()==1) return ch.get(0);
	    if(changed) return new Max(ch);
	    else return this;
	}
	@Override
	public ASTNode normalise() {
	    // sort by hashcode 
        // Insertion sort -- behaves well with almost-sorted lists
        ArrayList<ASTNode> ch=getChildren();
        boolean changed=sortByHashcode(ch);
        
        if(changed) return new Max(ch);
        else return this;
    }
    
	public boolean typecheck(SymbolTable st) {
	    for(int i=0; i<numChildren(); i++) {
	        if(!getChild(i).typecheck(st)) return false;
	        if(getChild(i).getDimension()>0) {
	            CmdFlags.println("ERROR: Unexpected matrix in max: "+this);
	            return false;
	        }
        }
        return true;
    }
	
    public Intpair getBounds() {
        // The lower bound is the max of all lower bounds of children,
	    // and similarly for the upper bound.
	    Intpair a=getChild(0).getBounds();
	    long lower=a.lower;
	    long upper=a.upper;
	    
	    for(int i=1; i<numChildren(); i++) {
	        Intpair b=getChild(i).getBounds();
	        if(b.lower>lower) lower=b.lower;
	        if(b.upper>upper) upper=b.upper;
	    }
	    a.lower=lower;
	    a.upper=upper;
	    return lookupBounds(a);    //  Look up in FilteredDomainStore	    
    }
	public PairASTNode getBoundsAST() {
	    // The lower bound is the max of all lower bounds of children,
	    // and similarly for the upper bound.
	    ArrayList<ASTNode> max_lbs=new ArrayList<ASTNode>();
	    ArrayList<ASTNode> max_ubs=new ArrayList<ASTNode>();
	    
	    for(int i=0; i<numChildren(); i++) {
	        PairASTNode a=getChild(i).getBoundsAST();
	        max_lbs.add(a.e1);
	        max_ubs.add(a.e2);
	    }
	    return new PairASTNode(new Max(max_lbs), new Max(max_ubs));	    
	}
	
	public String toString() {
	    return generic_to_string("max");
	}
	public void toDominionParam(StringBuffer b) {
	    b.append("Max([");
	    for(int i=0; i<numChildren(); i++) {
	        getChild(i).toDominionParam(b);
	        if(i<numChildren()-1) b.append(",");
	    }
	    b.append("])");
	}
	public void toDominionWithAuxVar(StringBuffer b, ASTNode aux) {
	    b.append(CmdFlags.getCtName()+" ");
	    b.append("max([");
	    for(int i=0; i<numChildren(); i++) {
	        getChild(i).toDominion(b, false);
	        if(i<numChildren()-1) b.append(",");
	    }
	    b.append("],");
	    aux.toDominion(b, false);
	    b.append(")");
	}
	public void toMinionWithAuxVar(StringBuffer b, ASTNode aux) {
	    b.append("max([");
	    for(int i=0; i<numChildren(); i++) {
	        getChild(i).toMinion(b, false);
	        if(i<numChildren()-1) b.append(",");
	    }
	    b.append("],");
	    aux.toMinion(b, false);
	    b.append(")");
	}
	public void toFlatzincWithAuxVar(StringBuffer b, ASTNode aux) {
	    assert numChildren()==2;
	    b.append("constraint int_max(");
	    for(int i=0; i<numChildren(); i++) {
	        getChild(i).toFlatzinc(b, false);
	        if(i<numChildren()-1) b.append(",");
	    }
	    b.append(",");
	    aux.toFlatzinc(b, false);
	    b.append(");");
	}
	public void toMinizinc(StringBuffer b, boolean bool_context) {
	    b.append("max([");
	    for(int i=0; i<numChildren(); i++) {
	        getChild(i).toMinizinc(b, bool_context);
	        if(i<numChildren()-1) b.append(",");
	    }
	    
	    b.append("])");
	}
	
    public boolean childrenAreSymmetric() {
        return true;
    }
}
